import java.nio.ByteBuffer;
import java.util.Arrays;

public class Empacotador {

    public static byte[] criarPacote(byte tipo, int numSeq, int numAck, byte[] dados) {
        short tamanhoDados = (short) (dados != null ? dados.length : 0);
        ByteBuffer buffer = ByteBuffer.allocate(Constantes.TAMANHO_CABECALHO + tamanhoDados);

        buffer.put(tipo);
        buffer.putInt(numSeq);
        buffer.putInt(numAck);
        buffer.putShort(tamanhoDados);

        if (dados != null && tamanhoDados > 0) {
            buffer.put(dados);
        }
        return buffer.array();
    }

    public static Pacote extrairPacote(byte[] dadosRecebidos, int tamanho) {
        ByteBuffer buffer = ByteBuffer.wrap(dadosRecebidos, 0, tamanho);

        byte tipo = buffer.get();
        int numSeq = buffer.getInt();
        int numAck = buffer.getInt();
        short tamanhoDados = buffer.getShort();

        byte[] payload = null;
        if (tamanhoDados > 0) {
            payload = new byte[tamanhoDados];
            buffer.get(payload);
        }

        return new Pacote(tipo, numSeq, numAck, payload);
    }

    // Classe auxiliar para trafegar os dados desempacotados
    public static class Pacote {
        public byte tipo;
        public int numSeq;
        public int numAck;
        public byte[] payload;

        public Pacote(byte tipo, int numSeq, int numAck, byte[] payload) {
            this.tipo = tipo;
            this.numSeq = numSeq;
            this.numAck = numAck;
            this.payload = payload;
        }
    }
}