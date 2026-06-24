import java.security.MessageDigest;
import java.io.FileInputStream;

public class GerenciadorHash {
    public static String calcularMD5(String caminhoArquivo) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        FileInputStream fis = new FileInputStream(caminhoArquivo);
        byte[] buffer = new byte[8192];
        int bytesLidos;

        while ((bytesLidos = fis.read(buffer)) != -1) {
            md.update(buffer, 0, bytesLidos);
        }
        fis.close();

        byte[] hashBytes = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}