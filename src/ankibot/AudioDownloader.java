package ankibot;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AudioDownloader {

    private final Path pastaDestino;
    private final int maxTentativas;
    private final int timeoutMs;

    public AudioDownloader(Path pastaDestino, int maxTentativas, int timeoutMs) {
        this.pastaDestino = pastaDestino;
        this.maxTentativas = maxTentativas;
        this.timeoutMs = timeoutMs;
    }

    public boolean baixar(String texto, String nomeArquivo) {
        Path caminhoCompleto = pastaDestino.resolve(nomeArquivo);

        // Já existe: pula (permite retomar execução interrompida sem re-baixar tudo)
        if (Files.exists(caminhoCompleto) && caminhoCompleto.toFile().length() > 0) {
            return true;
        }

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                baixarUmaVez(texto, caminhoCompleto);
                return true;
            } catch (Exception e) {
                if (tentativa == maxTentativas) {
                    System.err.println("   [ERRO] Falha definitiva ao baixar áudio: " + texto
                            + " -> " + e.getMessage());
                    return false;
                }
                aguardarBackoff(tentativa);
            }
        }
        return false;
    }

    private void baixarUmaVez(String texto, Path destino) throws Exception {
        String textoCodificado = URLEncoder.encode(texto, StandardCharsets.UTF_8);
        String urlFinal = "https://translate.google.com/translate_tts?ie=UTF-8&tl=en&client=tw-ob&q="
                + textoCodificado;

        URL url = URI.create(urlFinal).toURL();
        HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
        conexao.setRequestProperty("User-Agent", "Mozilla/5.0");
        conexao.setConnectTimeout(timeoutMs);
        conexao.setReadTimeout(timeoutMs);

        try (InputStream in = new BufferedInputStream(conexao.getInputStream())) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void aguardarBackoff(int tentativa) {
        try {
            // backoff crescente + jitter, evita martelar o servidor
            Thread.sleep(500L * tentativa + (long) (Math.random() * 300));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}