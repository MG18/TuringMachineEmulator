import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Universal 1‑Band‑Turing‑Maschine — Emulator
 * ===========================================
 * Dieses Programm emuliert **jede** deterministische Turing‑Maschine, deren
 * Übergangsfunktion gemäß der in der Vorlesung (Teil‑6, HO) eingeführten
 * Binärkodierung gegeben ist.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * # Eingabe
 * ────────────────────────────────────────────────────────────────────────────
 * ▸ <transitions>111<input>         … als komplette Zeichenreihe **oder**
 * ▸ input.txt (gleiche Struktur)    … Standard‑Datei.
 *
 * <input> darf
 *   • bereits binär (0/1) **oder**
 *   • dezimal (Ziffern 0‑9) sein.
 * Dezimale Eingaben werden **automatisch** nach Binär konvertiert, sobald
 * mindestens eine Dezimalziffer ›2‹–›9‹ vorkommt (Heuristik aus der Übung).
 *
 * ────────────────────────────────────────────────────────────────────────────
 * # Ausführung‑Modi
 * ────────────────────────────────────────────────────────────────────────────
 * Beim Start fragt das Programm einmalig:
 *     »Step‑Modus? (j/n):«
 * • **j** → Step‑Modus  (Pause 300 ms & Status nach jedem Schritt)
 * • **n** → Lauf‑Modus  (alle Schritte ohne Halt; Abschluss‑Summary)
 *
 * ────────────────────────────────────────────────────────────────────────────
 * # Ausgabe (Pflichtenheft a‑e)
 * ────────────────────────────────────────────────────────────────────────────
 * a) Ergebnis (ACCEPTED | REJECTED)
 * b) Aktueller Zustand qᵢ
 * c) Band‑Ausschnitt ±15 Zellen um den Kopf
 * d) Kopfposition (Index)
 * e) Schrittzähler
 *
 * Grafische Spielereien (f) sind *nicht* implementiert, können aber über das
 * Datenmodell leicht ergänzt werden.
 * ---------------------------------------------------------------------------
 */
public class UtmEmulator {
    /* Konstanten ---------------------------------------------------------- */
    private static final int START_STATE    = 1;
    private static final int ACCEPT_STATE   = 2;    // akzeptierend (läuft weiter!)
    private static final int WINDOW         = 15;   // ±15 Zellen Band‑Fenster
    private static final int STEP_DELAY_MS  = 300;  // Verzögerung im Step‑Modus

    /* Transition‑Record --------------------------------------------------- */
    private record Transition(int nextState, char writeSym, int moveDir) {}

    /* Delta‑Funktion: key="q,sym" → (alle Übergänge in Einfügereihenfolge) */
    private static Map<String, List<Transition>> delta;

    /* --------------------------------------------------------------------- */
    public static void main(String[] args) {
        /* ------------------------------------------------------------
         * 1) Quell‑Kodierung besorgen
         * ------------------------------------------------------------ */
        String encoded;
        if (args.length > 0) {
            // Erste CLI‑Zeichenkette als Kodierung interpretieren
            encoded = String.join(" ", args).strip();
        } else {
            // Fallback: input.txt lesen (leer → Fehlermeldung)
            try {
                encoded = Files.readString(Path.of("input.txt")).strip();
            } catch (IOException e) {
                System.err.println("FEHLER: input.txt konnte nicht gelesen werden: " + e.getMessage());
                return;
            }
        }

        if(encoded.startsWith("1")){
            encoded = encoded.substring(1);
        }

        /* ------------------------------------------------------------
         * 2) Programmkodierung / Input voneinander trennen
         * ------------------------------------------------------------ */
        int sep = encoded.indexOf("111");
        if (sep < 0) {
            System.err.println("FEHLER: Kein '111'‑Separator gefunden.");
            return;
        }

        String transEnc = encoded.substring(0, sep);
        String payload  = encoded.substring(sep + 3);

        /* ------------------------------------------------------------
         * 3) Eingabe auf Binärformat normalisieren
         * ------------------------------------------------------------ */
        payload = payload.strip();
        payload = autoConvertDecimalToBinary(payload);

        /* ------------------------------------------------------------
         * 4) Ausführungsmodus wählen (Step/Run)
         * ------------------------------------------------------------ */
        boolean stepMode = askForStepMode();

        /* ------------------------------------------------------------
         * 5) Übergänge parsen & Simulation starten
         * ------------------------------------------------------------ */
        try {
            delta = parseTransitions(transEnc);
            run(payload, stepMode);
        } catch (IllegalArgumentException | InterruptedException e) {
            System.err.println("FEHLER: " + e.getMessage());
        }
    }

    /* –– Hilfsroutinen –– */

    /** Konvertiert automatisch Dezimal → Binär, wenn offenbar dezimal. */
    private static String autoConvertDecimalToBinary(String s) {
        if (s.isEmpty()) return "";
        // Enthält s ausschließlich Ziffern 0‑9 *und* mindestens eine von 2‑9?
        if (Pattern.matches("[0-9]+", s) && Pattern.compile("[2-9]").matcher(s).find()) {
            return Integer.toBinaryString(Integer.parseInt(s));
        }
        return s; // bereits Binär (0/1) oder eindeutiger Mix
    }

    /** Fragt interaktiv ab, ob der Step‑Modus gewählt wird. */
    private static boolean askForStepMode() {
        System.out.print("Step‑Modus? (j/n): ");
        return new Scanner(System.in).nextLine().trim().equalsIgnoreCase("j");
    }

    /**
     * Parsed die Übergangszeichenkette gemäß Vorlesungs‑Kodierung.
     * Erlaubt exakt fünf Blöcke aus 0ᶦ, getrennt durch EINZELNE '1'.
     * Mehrfach‑Übergänge werden mit "11" voneinander abgegrenzt.
     */
    private static Map<String, List<Transition>> parseTransitions(String enc) {
        Map<String, List<Transition>> map = new HashMap<>();
        for (String t : enc.split("11")) {
            if (t.isEmpty()) continue;
            String[] z = t.split("1");
            if (z.length != 5) {
                throw new IllegalArgumentException("Ungültiges Übergangssegment: " + t);
            }
            int i = z[0].length(); // qᵢ
            int j = z[1].length(); // Xⱼ (Read)
            int k = z[2].length(); // qₖ
            int l = z[3].length(); // Xˡ (Write)
            int m = z[4].length(); // Dₘ (Dir)

            char read  = symbolFor(j);
            char write = symbolFor(l);
            int dir = switch (m) {
                case 1 -> -1; // L
                case 2 ->  1; // R
                default -> throw new IllegalArgumentException("Bewegungsrichtung D" + m + " ungültig (nur 1=L, 2=R erlaubt)");
            };
            String key = i + "," + read;
            map.computeIfAbsent(key, _ -> new ArrayList<>())
                    .add(new Transition(k, write, dir)); // Einfügereihenfolge bewahren
        }
        return map;
    }

    /** Wandelt vorlesungskonforme Zähl‑Kodierung → tatsächliches Bandsymbol. */
    private static char symbolFor(int n) {
        return switch (n) {
            case 1 -> '0';
            case 2 -> '1';
            case 3 -> '_';
            default -> (char) ('A' + n - 4); // A, B, … für erweiterte Alphabete
        };
    }

    /** Hauptsimulation – führt solange aus, bis kein Übergang mehr anwendbar ist. */
    private static void run(String input, boolean stepMode) throws InterruptedException {
        /* Tape initialisieren */
        TreeMap<Integer, Character> tape = new TreeMap<>();
        for (int i = 0; i < input.length(); i++) tape.put(i, input.charAt(i));

        int head  = 0;
        int state = START_STATE;
        long steps = 0;

        while (true) {
            if (stepMode) {
                printStatus(tape, head, state, steps);
                Thread.sleep(STEP_DELAY_MS);
            }
            char sym = tape.getOrDefault(head, '_');
            List<Transition> list = delta.get(state + "," + sym);
            if (list == null || list.isEmpty()) break; // HALT

            Transition tr = list.get(0); // Erster gültiger Übergang
            tape.put(head, tr.writeSym());
            head  += tr.moveDir();
            state  = tr.nextState();
            steps++;
        }

        /* Abschlussausgabe (a‑e) */
        System.out.println("HALT  →  " + (state == ACCEPT_STATE ? "ACCEPTED" : "REJECTED")); // a
        System.out.println("Zustand : q" + state);                                                 // b
        System.out.println("Schritte : " + steps);                                                  // e
        printWindow(tape, head);                                                                    // c+d
    }

    /* ------------------------------------------------------------ */
    /* Komfort‑Ausgaben                                             */
    /* ------------------------------------------------------------ */
    private static void printStatus(TreeMap<Integer, Character> tape, int head, int st, long step) {
        System.out.printf("Step %d | q%d%n", step, st);
        printWindow(tape, head);
    }

    private static void printWindow(TreeMap<Integer, Character> tape, int head) {
        StringBuilder sb = new StringBuilder();
        for (int p = head - WINDOW; p <= head + WINDOW; p++) sb.append(tape.getOrDefault(p, '_'));
        System.out.println(sb);
        System.out.println(" ".repeat(WINDOW) + "^");
        System.out.println("Head   : " + head);
        System.out.println("-".repeat(WINDOW * 2 + 1));
    }
}
