package ankibot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

public class AnkiWriter {

    private final Path arquivoSaida;

    public AnkiWriter(Path arquivoSaida) {
        this.arquivoSaida = arquivoSaida;
    }

    public String formatarLinha(Card card) {
        // Escapa o separador '|' para não quebrar a importação no Anki
        String traducaoSegura = card.traducao().replace("|", "/");
        String fraseSegura = card.frase().replace("|", "/");
        String termoSeguro = card.termo().replace("|", "/");

        String verso = traducaoSegura + "<br><br>" + fraseSegura
                + " [sound:" + card.nomeMp3Termo() + "] [sound:" + card.nomeMp3Frase() + "]";

        return termoSeguro + "|" + verso;
    }

    public void escrever(List<String> linhasFormatadas) throws IOException {
        try (PrintWriter escritor = new PrintWriter(new FileWriter(arquivoSaida.toFile()))) {
            for (String linha : linhasFormatadas) {
                if (linha != null) {
                    escritor.println(linha);
                }
            }
        }
    }
}