import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Corretor {
    private final Set<String> dicionario = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<Character, List<String>> dicionarioPorInicial = new HashMap<>();

    public Corretor() {
        carregarDicionario();
    }

    private void carregarDicionario() {
        try (InputStream is = getClass().getResourceAsStream("/dicionario.txt")) {
            if (is == null) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    linha = linha.trim();
                    if (!linha.isEmpty()) {
                        dicionario.add(linha);

                        char initial = Character.toLowerCase(linha.charAt(0));
                        dicionarioPorInicial
                            .computeIfAbsent(initial, k -> new ArrayList<>())
                            .add(linha);
                    }
                }
            }
        } catch (IOException e) {
            // ignora
        }
    }

    public boolean isPalavraValida(String palavra) {
        return dicionario.contains(palavra);
    }

    public String sugerirCorrecao(String wordLower) {
        if (wordLower == null || wordLower.isEmpty()) return null;

        char initial = wordLower.charAt(0);
        List<String> candidates = dicionarioPorInicial.get(initial);
        if (candidates == null) {
            candidates = new ArrayList<>(dicionario);
        }

        String melhor = null;
        int melhorDist = Integer.MAX_VALUE;
        int wordLen = wordLower.length();

        for (String dictWord : candidates) {
            int dictLen = dictWord.length();
            if (Math.abs(dictLen - wordLen) > 2) continue;

            int dist = levenshtein(wordLower, dictWord.toLowerCase(Locale.ROOT));
            if (dist < melhorDist) {
                melhorDist = dist;
                melhor = dictWord;
                if (melhorDist == 0) break;
            }
        }

        return melhorDist <= 2 ? melhor : null;
    }

    public String aplicarCapitalizacao(String original, String correcao) {
        if (original.equals(original.toUpperCase(Locale.ROOT))) {
            return correcao.toUpperCase(Locale.ROOT);
        }
        if (Character.isUpperCase(original.charAt(0))
            && original.substring(1).equals(original.substring(1).toLowerCase(Locale.ROOT))) {
            return correcao.substring(0, 1).toUpperCase(Locale.ROOT)
                + correcao.substring(1).toLowerCase(Locale.ROOT);
        }
        return correcao;
    }

    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int prevCost = costs[0];
            costs[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int currentCost = costs[j];
                int insertCost = costs[j - 1] + 1;
                int deleteCost = costs[j] + 1;
                int replaceCost = prevCost + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                costs[j] = Math.min(Math.min(insertCost, deleteCost), replaceCost);
                prevCost = currentCost;
            }
        }
        return costs[b.length()];
    }
}