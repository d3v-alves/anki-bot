package ankibot;

public record Card(String termo, String traducao, String frase) {

    public static Card fromLinha(String linha) {
        if (linha == null || linha.trim().isEmpty() || !linha.contains("\t")) {
            return null;
        }
        String[] partes = linha.split("\\t", -1);
        if (partes.length < 3) {
            return null;
        }
        String termo = partes[0].trim();
        if (termo.isEmpty()) {
            return null;
        }
        return new Card(termo, partes[1].trim(), partes[2].trim());
    }

    public String nomeBase() {
        return termo.replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "");
    }

    public String nomeMp3Termo() {
        return nomeBase() + ".mp3";
    }

    public String nomeMp3Frase() {
        return "frase_" + nomeBase() + ".mp3";
    }
}