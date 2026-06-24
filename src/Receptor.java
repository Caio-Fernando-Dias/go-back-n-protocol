import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receptor {

    private static final Logger LOGGER = Logger.getLogger(Receptor.class.getName());

    public static void main(String[] args) {
        int porta = 5000;

        FileOutputStream fos = null;

        System.out.println("Receptor a aguardar na porta " + porta + " pelo HANDSHAKE...");

        try (DatagramSocket socket = new DatagramSocket(porta)) {

            int expectedSeqNum = 0;
            byte[] bufferRecebimento = new byte[Constantes.TAMANHO_CABECALHO + Constantes.TAMANHO_MAX_DADOS];
            boolean recebendo = true;
            boolean handshakeRecebido = false;

            // Variáveis para Estatísticas
            double probPerda = 0.0;
            int pacotesRecebidos = 0;
            int pacotesDescartados = 0;
            String arquivoSaida = "";

            while (recebendo) {
                DatagramPacket packet = new DatagramPacket(bufferRecebimento, bufferRecebimento.length);
                socket.receive(packet);

                Empacotador.Pacote p = Empacotador.extrairPacote(packet.getData(), packet.getLength());

                if (p.tipo == Constantes.TIPO_HANDSHAKE && !handshakeRecebido) {
                    String payloadStr = new String(p.payload);
                    String[] parametros = payloadStr.split(";");

                    probPerda = Double.parseDouble(parametros[0]);
                    long tamanhoFicheiro = Long.parseLong(parametros[1]);
                    arquivoSaida = parametros[2];

                    fos = new FileOutputStream(arquivoSaida);
                    handshakeRecebido = true;

                    System.out.println("\n[HANDSHAKE] Parâmetros de sessão estabelecidos:");
                    System.out.println(" -> Ficheiro: " + arquivoSaida + " (" + tamanhoFicheiro + " bytes)");
                    System.out.println(" -> Probabilidade de perda: " + (probPerda * 100) + "%");

                } else if (p.tipo == Constantes.TIPO_FIN) {
                    System.out.println("\n[FIN] Ficheiro recebido na totalidade. A encerrar a ligação...");
                    recebendo = false;

                    // Fechamos o ficheiro e validamos o MD5 para garantir a integridade (Requisito R9)
                    if (fos != null) {
                        fos.close();
                    }
                    System.out.println("A verificar integridade dos dados...");
                    System.out.println("MD5 Destino: " + GerenciadorHash.calcularMD5(arquivoSaida));

                } else if (p.tipo == Constantes.TIPO_DADOS) {
                    if (!handshakeRecebido) continue;

                    if (p.numSeq == expectedSeqNum) {
                        if (Math.random() < probPerda) {
                            pacotesDescartados++;
                            System.out.println("[PERDA SIMULADA] Pacote " + p.numSeq + " descartado.");
                            continue;
                        }

                        pacotesRecebidos++;
                        System.out.println("Recebido Seq: " + p.numSeq + " (Em ordem). A gravar...");
                        fos.write(p.payload);
                        fos.flush();

                        enviarAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum);
                        expectedSeqNum++;

                    } else {
                        System.out.println("Recebido Seq: " + p.numSeq + " (Fora de ordem). Esperado: " + expectedSeqNum);
                        enviarAck(socket, packet.getAddress(), packet.getPort(), expectedSeqNum - 1);
                    }
                }
            }

            // Exibição de Estatísticas Finais
            System.out.println("\n================ ESTATÍSTICAS DO RECEPTOR ================");
            System.out.println("Total de pacotes originais recebidos e gravados: " + pacotesRecebidos);
            System.out.println("Total de pacotes descartados (simulação): " + pacotesDescartados);
            int totalProcessado = pacotesRecebidos + pacotesDescartados;
            double taxaEfetiva = totalProcessado == 0 ? 0 : (double) pacotesDescartados / totalProcessado * 100.0;
            System.out.printf("Taxa de perda efetiva: %.2f%%\n", taxaEfetiva);
            System.out.println("==========================================================");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Ocorreu um erro no processamento do Receptor", e);
        } finally {
            // Fecho do recurso no bloco finally com log robusto (resolve os 2 warnings)
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Erro ao fechar o FileOutputStream", e);
                }
            }
        }
    }

    private static void enviarAck(DatagramSocket socket, InetAddress endereco, int porta, int numAck) throws Exception {
        byte[] dadosAck = Empacotador.criarPacote(Constantes.TIPO_ACK, 0, numAck, null);
        DatagramPacket ackPacket = new DatagramPacket(dadosAck, dadosAck.length, endereco, porta);
        socket.send(ackPacket);
    }
}