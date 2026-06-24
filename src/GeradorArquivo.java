import java.io.FileOutputStream;
import java.io.IOException;

public class GeradorArquivo {
    public static void main(String[] args) {
        String nomeArquivo = "../data/arquivo_teste.txt";

        long tamanhoAlvo = 30 * 1024;

        System.out.println("Gerando arquivo leve para testes basicos (30 KB)...");

        try (FileOutputStream fos = new FileOutputStream(nomeArquivo)) {
            long bytesEscritos = 0;
            int numeroBloco = 0;

            while (bytesEscritos < tamanhoAlvo) {
                String prefixo = String.format("[BLOCO %03d] GBN Teste - ", numeroBloco);
                StringBuilder linha = new StringBuilder(prefixo);

                char preenchimento = (char) ('A' + (numeroBloco % 26));
                while (linha.length() < 127) {
                    linha.append(preenchimento);
                }
                linha.append("\n");

                byte[] dados = linha.toString().getBytes();
                fos.write(dados);
                bytesEscritos += dados.length;
                numeroBloco++;
            }
            System.out.println("Arquivo de 30 KB gerado com sucesso!");
            System.out.println("Isso resultara em exatamente 30 pacotes (Seq: 0 a 29).");
        } catch (IOException e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }
}