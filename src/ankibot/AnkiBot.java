package ankibot;

import java.awt.HeadlessException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

public class AnkiBot {

    // ==== Configurações ====
    static final Path PASTA_AUDIOS = Paths.get("audios");
    static final Path ARQUIVO_SAIDA = Paths.get("importar_no_anki.txt");
    static final Path ARQUIVO_FALHAS = Paths.get("falhas.log");
    static final int NUM_THREADS = 5;      // downloads simultâneos
    static final int MAX_TENTATIVAS = 3;   // retries por áudio
    static final int TIMEOUT_MS = 10_000;  // timeout de conexão/leitura

    public static void main(String[] args) throws Exception {
        Files.createDirectories(PASTA_AUDIOS);

        Path arquivoEntrada = escolherArquivoEntrada(args);
        if (arquivoEntrada == null) {
            System.out.println("Nenhum arquivo selecionado. Encerrando.");
            return;
        }

        List<String> linhasBrutas = Files.readAllLines(arquivoEntrada);
        List<Card> cards = new ArrayList<>();
        for (String linha : linhasBrutas) {
            cards.add(Card.fromLinha(linha)); // pode ser null (linha inválida/vazia)
        }

        long total = cards.stream().filter(c -> c != null).count();

        AudioDownloader downloader = new AudioDownloader(PASTA_AUDIOS, MAX_TENTATIVAS, TIMEOUT_MS);
        AnkiWriter writer = new AnkiWriter(ARQUIVO_SAIDA);

        AtomicInteger contador = new AtomicInteger(0);
        List<String> falhas = Collections.synchronizedList(new ArrayList<>());

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<String>> futuros = new ArrayList<>();

        for (Card cartao : cards) {
            if (cartao == null) {
                futuros.add(CompletableFuture.completedFuture(null));
                continue;
            }
            futuros.add(pool.submit(() ->
                    processarCartao(cartao, downloader, writer, contador, total, falhas)));
        }
        pool.shutdown();

        List<String> linhasFormatadas = new ArrayList<>();
        for (Future<String> f : futuros) {
            linhasFormatadas.add(f.get());
        }
        writer.escrever(linhasFormatadas);

        if (!falhas.isEmpty()) {
            try (PrintWriter logFalhas = new PrintWriter(new FileWriter(ARQUIVO_FALHAS.toFile()))) {
                for (String falha : falhas) {
                    logFalhas.println(falha);
                }
            }
        }

        System.out.println("\n--- PROCESSO CONCLUÍDO ---");
        System.out.println("Arquivo '" + ARQUIVO_SAIDA + "' gerado com sucesso.");
        if (!falhas.isEmpty()) {
            System.out.println(falhas.size() + " falha(s) registrada(s) em '" + ARQUIVO_FALHAS + "'.");
        }
    }

    private static Path escolherArquivoEntrada(String[] args) throws Exception {
        if (args.length > 0) {
            return Paths.get(args[0]);
        }

        try {
            return abrirSeletorDeArquivo();
        } catch (HeadlessException e) {
            return pedirCaminhoViaConsole();
        } catch (java.lang.reflect.InvocationTargetException e) {
            // invokeAndWait embrulha exceções da EDT (incluindo HeadlessException) nesse tipo
            if (e.getCause() instanceof HeadlessException) {
                return pedirCaminhoViaConsole();
            }
            throw e;
        }
    }

    private static Path pedirCaminhoViaConsole() {
        System.out.println("Ambiente sem interface gráfica detectado.");
        System.out.print("Digite o caminho do arquivo de wordlist: ");
        try (Scanner scanner = new Scanner(System.in)) {
            String caminho = scanner.nextLine().trim();
            return caminho.isEmpty() ? null : Paths.get(caminho);
        }
    }

    private static Path abrirSeletorDeArquivo() throws Exception {
        final Path[] resultado = new Path[1];

        Runnable tarefa = () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecione o arquivo de wordlist (termo, tradução, frase)");
            chooser.setFileFilter(new FileNameExtensionFilter("Arquivos de texto (*.txt)", "txt"));

            int opcao = chooser.showOpenDialog(null);
            if (opcao == JFileChooser.APPROVE_OPTION) {
                resultado[0] = chooser.getSelectedFile().toPath();
            }
        };

        // JFileChooser precisa rodar na Event Dispatch Thread do Swing
        if (SwingUtilities.isEventDispatchThread()) {
            tarefa.run();
        } else {
            SwingUtilities.invokeAndWait(tarefa);
        }

        return resultado[0];
    }

    private static String processarCartao(Card cartao, AudioDownloader downloader, AnkiWriter writer,
                                          AtomicInteger contador, long total, List<String> falhas) {
        boolean okTermo = downloader.baixar(cartao.termo(), cartao.nomeMp3Termo());
        boolean okFrase = downloader.baixar(cartao.frase(), cartao.nomeMp3Frase());

        int atual = contador.incrementAndGet();
        System.out.println("[" + atual + "/" + total + "] " + cartao.termo()
                + (okTermo && okFrase ? " OK" : " (com falha em áudio)"));

        if (!okTermo) {
            falhas.add("Falha no áudio do termo: " + cartao.termo());
        }
        if (!okFrase) {
            falhas.add("Falha no áudio da frase: " + cartao.frase() + " (termo: " + cartao.termo() + ")");
        }

        return writer.formatarLinha(cartao);
    }
}