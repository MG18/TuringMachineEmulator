import java.util.*;


public class UniversalTMEmulator {

    // --- 1) Hilfs-Types aus Aufgabe 1 ---
    private enum Dir {L, R}

    private static class Trans {
        final int nextState;
        final char write;
        final Dir dir;

        Trans(int ns, char w, Dir d) {
            nextState = ns;
            write = w;
            dir = d;
        }
    }

    // --- 2) Parser: nimmt den langen Binär-String und baut die Übergangstabelle ---
    private static Map<Integer, Map<Character, Trans>> parseTransitions(String code) {
        Map<Integer, Map<Character, Trans>> table = new HashMap<>();
        // Jede Regel ist durch "111" voneinander getrennt
        String[] rules = code.split("111");
        for (String rule : rules) {
            // Teile mit den einzelnen '1'-Trennungen
            // Achtung: das letzte Feld (Richtung) endet nicht mit '1'
            String[] parts = rule.split("1");
            if (parts.length != 5) {
                throw new RuntimeException("Invalid transition encoding: " + rule);
            }
            int from = parts[0].length();          // n Nullen → Zustand n
            char read = zerosToSymbol(parts[1].length());
            int to = parts[2].length();
            char write = zerosToSymbol(parts[3].length());
            Dir dir = parts[4].length() == 1 ? Dir.L : Dir.R;

            table
                    .computeIfAbsent(from, k -> new HashMap<>())
                    .put(read, new Trans(to, write, dir));
        }
        return table;
    }

    // Hilf: wandelt Anzahl von Nullen in das Band-Symbol um
    private static char zerosToSymbol(int zeros) {
        switch (zeros) {
            case 1:
                return '⊔';   // Blank
            case 2:
                return '1';
            case 3:
                return 'X';
            case 4:
                return 'Y';
            default:
                throw new RuntimeException("Unknown symbol encoding of length " + zeros);
        }
    }

    // --- 3) Emulator: führt die TM auf unärer Eingabe aus ---
    private static void simulate(
            Map<Integer, Map<Character, Trans>> trans,
            boolean stepMode,
            String input
    ) {
        // Tape als unendliches Array über HashMap
        Map<Integer, Character> tape = new HashMap<>();
        for (int i = 0; i < input.length(); i++) {
            tape.put(i, input.charAt(i));
        }
        int head = 0, state = 0;
        long steps = 0;
        final int ACCEPT = 5;  // q_accept ist 5 bei unserem Tquad-Entwurf

        // Lauf-Schleife
        while (true) {
            char c = tape.getOrDefault(head, '⊔');
            Trans tr = Optional.ofNullable(trans.get(state))
                    .map(m -> m.get(c))
                    .orElse(null);
            if (tr == null) break;  // keine Regel → halt

            // ausführen
            steps++;
            tape.put(head, tr.write);
            head += (tr.dir == Dir.R ? 1 : -1);
            state = tr.nextState;

            if (stepMode) {
                report(state, tape, head, steps, ACCEPT);
            }
        }

        // End-Ausgabe
        report(state, tape, head, steps, ACCEPT);
    }

    // Hilf: druckt Aufgabe 1-Ausgabe (a–e)
    private static void report(
            int state,
            Map<Integer, Character> tape,
            int head,
            long steps,
            int acceptState
    ) {
        // Bandsegment ±15
        StringBuilder seg = new StringBuilder();
        for (int i = head - 15; i <= head + 15; i++) {
            seg.append(tape.getOrDefault(i, '⊔'));
        }
        String result = (state == acceptState ? "AKZEPTIERT" : "ABGELEHNT");
        System.out.printf(
                "Ergebnis: %s  Zustand=q%d  Band=%s  Kopf=%d  Schritte=%d%n",
                result, state, seg, head, steps
        );
    }

    // --- 4) main: baut unseren Tquad automatisch auf und emuliert ---
    public static void main(String[] args) {
        // 4.1) Richte die Specs „von Hand“ ein
        List<Spec> specs = Arrays.asList(
                // q0
                new Spec(0, '1', 1, 'X', Dir.R),
                new Spec(0, 'X', 0, 'X', Dir.R),
                new Spec(0, '⊔', 5, '⊔', Dir.R),
                // q1
                new Spec(1, '1', 1, '1', Dir.R),
                new Spec(1, 'X', 1, 'X', Dir.R),
                new Spec(1, '⊔', 2, '⊔', Dir.L),
                // q2
                new Spec(2, '1', 2, '1', Dir.L),
                new Spec(2, 'X', 2, 'X', Dir.L),
                new Spec(2, '⊔', 3, '⊔', Dir.R),
                // q3
                new Spec(3, '1', 1, 'X', Dir.R),
                new Spec(3, 'X', 3, 'X', Dir.R),
                new Spec(3, '⊔', 5, '⊔', Dir.R)
        );

        // 4.2) Spezifikation → Binärstring
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            // 0^from + "1"
            code.append(repeat('0', s.fromState)).append('1');
            // read
            code.append(repeat('0', symbolToZeros(s.read))).append('1');
            // to
            code.append(repeat('0', s.toState)).append('1');
            // write
            code.append(repeat('0', symbolToZeros(s.write))).append('1');
            // dir: 0 für L, 00 für R
            code.append(s.dir == Dir.L ? "0" : "00");
            if (i + 1 < specs.size()) code.append("111");
        }
        String tmCode = code.toString();

        System.out.println("Tquad TM-Code:");
        System.out.println(tmCode);

        // 4.3) Parser
        Map<Integer, Map<Character, Trans>> trans = parseTransitions(tmCode);

        // die vier zu quadrierenden Zahlen
        int[] inputs = {2, 10, 11, 25};
        for (int n : inputs) {
            // unäre Kodierung erzeugen: '1' wiederholt n-mal
            String unaryInput = repeat('1', n);
            // für kleine n im Step-Modus, sonst im Lauf-Modus
            boolean stepMode = (n <= 2);
            System.out.printf("%n=== Quadrat von %d im %s-Modus ===%n",
                    n, stepMode ? "Step" : "Lauf");
            simulate(trans, stepMode, unaryInput);

        }
    }

        // ----- Hilfsklassen für main() -----
        private static class Spec {
            final int fromState;
            final char read;
            final int toState;
            final char write;
            final Dir dir;

            Spec(int fs, char r, int ts, char w, Dir d) {
                fromState = fs;
                read = r;
                toState = ts;
                write = w;
                dir = d;
            }
        }

        // für repeat-Usage
        private static String repeat ( char c, int n){
            char[] a = new char[n];
            Arrays.fill(a, c);
            return new String(a);
        }

        // umgekehrt zu zerosToSymbol
        private static int symbolToZeros ( char c){
            switch (c) {
                case '⊔':
                    return 1;
                case '1':
                    return 2;
                case 'X':
                    return 3;
                case 'Y':
                    return 4;
                default:
                    throw new RuntimeException("Unknown symbol " + c);
            }
        }
    }
