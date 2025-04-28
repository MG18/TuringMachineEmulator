import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class UniversalTMEmulatorAufgabe1 {

    private enum Dir { L, R }

    private static class Trans {
        int nextState;
        char write;
        Dir dir;
        Trans(int ns, char w, Dir d) { nextState = ns; write = w; dir = d; }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java UniversalTMEmulator <-run|-step> <TM_code|file> <input|decimal|\"\">");
            return;
        }
        boolean stepMode = args[0].equals("-step");
        String codeSpec = args[1];
        String inputSpec = args[2];

        // TM-Code einlesen (Datei oder direkt) und alle Nicht-Bits entfernen
        String raw = readCode(codeSpec);
        String code = raw.replaceAll("[^01]", "");
        if (code.isEmpty()) {
            System.err.println("Kein gültiger TM-Code gefunden.");
            return;
        }

        // Eingabe aufbereiten (dezimal→binär oder direkt)
        String input = inputSpec.matches("\\d+")
                ? new java.math.BigInteger(inputSpec).toString(2)
                : inputSpec;

        // Parser: sequentiell alle Transitionen einlesen
        Map<Integer, Map<Character, Trans>> delta = parseTransitions(code);

        // Simulation starten
        simulate(delta, stepMode, input);
    }

    private static String readCode(String spec) throws Exception {
        File f = new File(spec);
        if (f.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            return sb.toString();
        }
        return spec;
    }

    private static Map<Integer, Map<Character, Trans>> parseTransitions(String code) {
        Map<Integer, Map<Character, Trans>> delta = new HashMap<>();
        int idx = 0, n = code.length();
        while (idx < n) {
            // 1) q_from: i Nullen
            int i = 0;
            while (idx < n && code.charAt(idx)=='0') { i++; idx++; }
            if (i==0 || idx>=n || code.charAt(idx)!='1')
                throw new RuntimeException("Invalid transition encoding at pos "+idx);
            idx++; // '1'

            // 2) read sym: j Nullen
            int j=0;
            while (idx<n && code.charAt(idx)=='0') { j++; idx++; }
            if (j==0 || idx>=n || code.charAt(idx)!='1')
                throw new RuntimeException("Invalid transition encoding at pos "+idx);
            idx++;

            // 3) q_to: k Nullen
            int k=0;
            while (idx<n && code.charAt(idx)=='0') { k++; idx++; }
            if (k==0 || idx>=n || code.charAt(idx)!='1')
                throw new RuntimeException("Invalid transition encoding at pos "+idx);
            idx++;

            // 4) write sym: l Nullen
            int l=0;
            while (idx<n && code.charAt(idx)=='0') { l++; idx++; }
            if (l==0 || idx>=n || code.charAt(idx)!='1')
                throw new RuntimeException("Invalid transition encoding at pos "+idx);
            idx++;

            // 5) dir: m Nullen (1=Links, 2=Rechts)
            int m=0;
            while (idx<n && code.charAt(idx)=='0') { m++; idx++; }
            if (m<1 || m>2)
                throw new RuntimeException("Invalid head-direction at pos "+idx);
            Dir d = (m==1 ? Dir.L : Dir.R);

            // Symbole umwandeln
            char r = symbol(j);
            char w = symbol(l);

            // Übergang speichern
            delta.computeIfAbsent(i, x->new HashMap<>())
                    .put(r, new Trans(k, w, d));

            // Falls danach noch "11" Separator, überspringen
            if (idx+1<n && code.charAt(idx)=='1' && code.charAt(idx+1)=='1') {
                idx += 2;
            }
        }
        return delta;
    }

    private static char symbol(int c) {
        return switch(c) {
            case 1 -> '0';
            case 2 -> '1';
            case 3 -> '_';   // blank
            default -> throw new RuntimeException("Unknown symbol code: "+c);
        };
    }

    private static void simulate(Map<Integer, Map<Character, Trans>> delta,
                                 boolean stepMode,
                                 String input) {
        Map<Integer,Character> tape = new HashMap<>();
        for (int p=0; p<input.length(); p++)
            tape.put(p, input.charAt(p));

        int head=0, state=1;
        long steps=0;
        boolean accept=false;

        while (true) {
            if (stepMode) printConfig(tape, head, state, steps, false, false);
            if (state==2) { accept=true; break; }

            char r = tape.getOrDefault(head, '_');
            Trans t = delta.getOrDefault(state, Map.of()).get(r);
            if (t==null) { accept=(state==2); break; }

            // ausführen
            if (t.write=='_') tape.remove(head);
            else tape.put(head, t.write);
            head += (t.dir==Dir.R ? 1 : -1);
            state = t.nextState;
            steps++;
        }
        printConfig(tape, head, state, steps, true, accept);
    }

    private static void printConfig(Map<Integer, Character> tape,
                                    int head, int state,
                                    long steps,
                                    boolean finalPrint,
                                    boolean accepted) {
        StringBuilder seg = new StringBuilder();
        for (int i=head-15;i<=head+15;i++)
            seg.append(tape.getOrDefault(i,'_'));
        String res = finalPrint
                ? (accepted ? "Ergebnis: AKZEPTIERT  " : "Ergebnis: ABGELEHNT  ")
                : "";
        System.out.printf(
                "%sZustand=q%d  Band=%s  Kopf=%d  Schritte=%d%n",
                res, state, seg, head, steps
        );
    }
}
