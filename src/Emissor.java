import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Emissor {

    private static final Logger LOGGER = Logger.getLogger(Emissor.class.getName());

    private static final int PORTA_DESTINO = 5000;
    private static final Object FSM_LOCK = new Object();

    private static int base = 0;
    private static int nextSeqNum = 0;
    private static Timer timer;

    // Variáveis de Estado para o Ficheiro e Buffer Circular
    private static int tamanhoJanela_N;
    private static byte[][] bufferCircular;
    private static FileInputStream fis;

    // Estatísticas
    private static int acksRecebidos = 0;
    private static int totalRetransmissoes = 0;

    public static void main(String[] args) {

        if (args.length < 4) {
            System.out.println("Utilização correta:");
            System.out.println("java Emissor <ficheiro_origem> <IP_destino>:<path_destino> <tamanho_janela> <prob_perda>");
            return;
        }

        try {
            String arquivoOrigem = args[0];
            String[] destinoParts = args[1].split(":");
            InetAddress enderecoDestino = InetAddress.getByName(destinoParts[0]);
            String pathDestino = destinoParts[1];

            tamanhoJanela_N = Integer.parseInt(args[2]);
            double probPerda = Double.parseDouble(args[3]);

            File f = new File(arquivoOrigem);
            long tamanhoArquivo = f.length();

            System.out.println("A calcular o Hash MD5 do ficheiro original...");
            System.out.println("MD5 Origem: " + GerenciadorHash.calcularMD5(arquivoOrigem));

            // 1. Inicializar a leitura dinâmica do ficheiro e o Buffer Circular
            fis = new FileInputStream(arquivoOrigem);
            bufferCircular = new byte[tamanhoJanela_N][];

            // Cálculo seguro do número total de pacotes sem carregar os dados
            int totalPacotes = (int) Math.ceil((double) tamanhoArquivo / Constantes.TAMANHO_MAX_DADOS);
            if (totalPacotes == 0) totalPacotes = 1; // Garante o processamento de ficheiros vazios

            DatagramSocket socket = new DatagramSocket();

            long tempoInicio = System.currentTimeMillis();

            // 2. Envio do pacote HANDSHAKE
            String handshakeMsg = probPerda + ";" + tamanhoArquivo + ";" + pathDestino;
            byte[] handshakeData = Empacotador.criarPacote(Constantes.TIPO_HANDSHAKE, -1, 0, handshakeMsg.getBytes());
            socket.send(new DatagramPacket(handshakeData, handshakeData.length, enderecoDestino, PORTA_DESTINO));
            System.out.println("HANDSHAKE enviado com sucesso.");

            // 3. Criação da Thread dedicada à receção dos ACKs
            Thread receiverThread = criarThreadReceptora(socket, enderecoDestino, totalPacotes);
            receiverThread.start();

            // 4. Loop principal de envio (FSM do Emissor)
            while (base < totalPacotes) {
                synchronized (FSM_LOCK) {
                    boolean enviouNovoPacote = false;

                    while (nextSeqNum < base + tamanhoJanela_N && nextSeqNum < totalPacotes) {
                        enviarNovoPacote(socket, enderecoDestino);

                        if (base == nextSeqNum) {
                            iniciarTimer(socket, enderecoDestino);
                        }

                        nextSeqNum++;
                        enviouNovoPacote = true;
                    }

                    if (!enviouNovoPacote) {
                        FSM_LOCK.wait();
                    }
                }
            }

            receiverThread.join();

            // 5. Envio do pacote FIN
            byte[] finData = Empacotador.criarPacote(Constantes.TIPO_FIN, -2, 0, null);
            socket.send(new DatagramPacket(finData, finData.length, enderecoDestino, PORTA_DESTINO));

            long tempoTotal_ms = System.currentTimeMillis() - tempoInicio;
            double throughputKbps = (tamanhoArquivo * 8.0) / tempoTotal_ms;

            System.out.println("\n================ ESTATÍSTICAS DO EMISSOR ================");
            System.out.println("Tamanho da Janela (N): " + tamanhoJanela_N);
            System.out.println("Pacotes de dados enviados (originais): " + totalPacotes);
            System.out.println("Retransmissões efetuadas (timeouts): " + totalRetransmissoes);
            System.out.println("ACKs Recebidos: " + acksRecebidos);
            System.out.println("Tempo de Transferência: " + tempoTotal_ms + " ms");
            System.out.printf("Throughput estimado: %.2f Kbps\n", throughputKbps);
            System.out.println("=========================================================");

            fis.close();
            System.exit(0);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Erro fatal no inicializador do Emissor", e);
        }
    }

    private static Thread criarThreadReceptora(DatagramSocket socket, InetAddress enderecoDestino, int totalPacotes) {
        return new Thread(() -> {
            try {
                byte[] bufferAck = new byte[Constantes.TAMANHO_CABECALHO];
                while (base < totalPacotes) {
                    DatagramPacket ackPacket = new DatagramPacket(bufferAck, bufferAck.length);
                    socket.receive(ackPacket);

                    Empacotador.Pacote p = Empacotador.extrairPacote(ackPacket.getData(), ackPacket.getLength());
                    if (p.tipo == Constantes.TIPO_ACK) {
                        acksRecebidos++;
                        System.out.println("Recebido ACK: " + p.numAck);

                        synchronized (FSM_LOCK) {
                            base = p.numAck + 1;
                            if (base == nextSeqNum) {
                                pararTimer();
                            } else {
                                iniciarTimer(socket, enderecoDestino);
                            }
                            FSM_LOCK.notifyAll();
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Erro na thread de receção de ACKs", e);
            }
        });
    }

    // Método otimizado para ler do disco paulatinamente
    private static void enviarNovoPacote(DatagramSocket socket, InetAddress endereco) throws Exception {
        byte[] dadosReais;
        byte[] bufferLeitura = new byte[Constantes.TAMANHO_MAX_DADOS];
        int bytesLidos = fis.read(bufferLeitura);

        if (bytesLidos != -1) {
            dadosReais = new byte[bytesLidos];
            System.arraycopy(bufferLeitura, 0, dadosReais, 0, bytesLidos);
        } else {
            dadosReais = new byte[0];
        }

        // Armazena o pacote gerado no buffer circular de forma rotativa usando módulo
        byte[] pacote = Empacotador.criarPacote(Constantes.TIPO_DADOS, nextSeqNum, 0, dadosReais);
        bufferCircular[nextSeqNum % tamanhoJanela_N] = pacote;

        DatagramPacket packet = new DatagramPacket(pacote, pacote.length, endereco, PORTA_DESTINO);
        socket.send(packet);
        System.out.println("Enviado pacote Seq: " + nextSeqNum);
    }

    private static void iniciarTimer(DatagramSocket socket, InetAddress endereco) {
        pararTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                synchronized (FSM_LOCK) {
                    System.out.println("\n[TIMEOUT] Retransmitir janela a partir da base: " + base);
                    try {
                        iniciarTimer(socket, endereco);
                        for (int i = base; i < nextSeqNum; i++) {
                            // Extrai os dados do Buffer Circular em vez de os reler do disco
                            byte[] pacoteRetransmitido = bufferCircular[i % tamanhoJanela_N];
                            DatagramPacket packet = new DatagramPacket(pacoteRetransmitido, pacoteRetransmitido.length, endereco, PORTA_DESTINO);
                            socket.send(packet);
                            totalRetransmissoes++;
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Erro durante a retransmissão", e);
                    }
                    FSM_LOCK.notifyAll();
                }
            }
        }, Constantes.TIMEOUT_MS);
    }

    private static void pararTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }
}