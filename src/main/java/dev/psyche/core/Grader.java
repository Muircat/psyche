package dev.psyche.core;

import java.text.Normalizer;
import java.util.*;

/**
 * Marks a typed answer offline. An answer is right when it mentions enough of the challenge's key ideas
 * (each idea is a list of accepted phrasings), in any wording around them. Words are compared after light
 * stemming (assimilate / assimilated / assimilating) and British/American spelling folding (behaviour,
 * organised). A slip of the fingers in a longer word is forgiven with a warning, but never when the typed
 * word is itself another course term (microsystem is not a typo of macrosystem).
 */
public final class Grader {
    /** Best first. TYPO is accepted with a warning. */
    public enum Verdict { EXACT, TYPO, WRONG }

    /** A misspelt word and the course word it was taken for. */
    public record Fix(String typed, String meant) {}

    /**
     * hits: which of the challenge's ideas the answer covers; trap: the known misconception it shows, if any;
     * mixed: other course terms it uses that aren't part of this answer (to explain what they mean).
     */
    public record Result(Verdict verdict, boolean[] hits, int needed, boolean order, List<Fix> fixes, Content.Trap trap, List<Content.Term> mixed) {
        public boolean ok() { return verdict != Verdict.WRONG; }
        public int count() { int n = 0; for (boolean h : hits) if (h) n++; return n; }
    }

    private static final Set<String> NEGATORS = Set.of("not", "no", "never", "isnt", "isn", "arent", "aren", "doesnt", "doesn",
            "dont", "don", "didnt", "didn", "cannot", "cant", "wont", "unlike", "without", "neither", "nor");

    /** Checks a typed answer. */
    public static Result check(String input, Content.Challenge c, Content content) { return check(input, c, content, false); }

    /**
     * Checks a typed answer. fill: it's what was typed into the cloze blank, so the key ideas are looked for in the
     * whole completed sentence, while misconceptions and other terms are looked for only in what was typed.
     */
    public static Result check(String input, Content.Challenge c, Content content, boolean fill) {
        List<String> typed = tokens(input);
        List<String> u = fill && c.hasCloze() ? tokens(c.prefix() + " " + input + " " + c.suffix()) : typed;
        Set<String> known = content.known();
        List<Content.Idea> need = c.ideas();
        // In a one-idea answer a negated course term doesn't count ("not assimilation"). Other words, and anything in a
        // longer explanation, still do: in "it can't show causes" or "infants can't consent", the negative is the idea.
        boolean terms = c.shortAnswer();
        boolean[] hits = new boolean[need.size()];
        int[] at = new int[need.size()];
        for (int g = 0; g < need.size(); g++) {
            at[g] = Integer.MAX_VALUE;
            for (String phrase : need.get(g).any()) {
                int pos = find(u, tokens(phrase), known, new ArrayList<>(), false, terms);
                if (pos >= 0 && pos < at[g]) { at[g] = pos; hits[g] = true; }
            }
        }
        int n = 0;
        for (boolean h : hits) if (h) n++;
        boolean ordered = true;
        if (c.order) {
            int last = -1;
            for (int g = 0; g < need.size(); g++) if (hits[g]) { if (at[g] < last) ordered = false; last = at[g]; }
        }
        Content.Trap trap = null;
        for (Content.Trap t : c.traps) if (t.any != null && hit(typed, t.any, known)) { trap = t; break; }
        boolean ok = n >= c.needed() && ordered && (trap == null || !trap.veto);
        List<Fix> real = ok ? exactFixes(u, need, hits, known, terms) : List.of();
        Verdict v = !ok ? Verdict.WRONG : real.isEmpty() ? Verdict.EXACT : Verdict.TYPO;
        List<Content.Term> mixed = ok ? List.of() : mixed(typed, c, content, known);
        return new Result(v, hits, c.needed(), ordered, real, ok ? null : trap, mixed);
    }

    /** The typo fixes behind a correct answer: only for ideas that no phrasing matched exactly. */
    private static List<Fix> exactFixes(List<String> u, List<Content.Idea> need, boolean[] hits, Set<String> known, boolean terms) {
        List<Fix> out = new ArrayList<>();
        for (int g = 0; g < need.size(); g++) {
            if (!hits[g]) continue;
            List<Fix> best = null;
            for (String phrase : need.get(g).any()) {
                List<Fix> f = new ArrayList<>();
                if (find(u, tokens(phrase), known, f, false, terms) >= 0 && (best == null || f.size() < best.size())) best = f;
                if (best != null && best.isEmpty()) break;
            }
            if (best != null) out.addAll(best);
        }
        return out;
    }

    /** Course terms used in a wrong answer that this challenge doesn't ask for (nor mention in its question). */
    private static List<Content.Term> mixed(List<String> u, Content.Challenge c, Content content, Set<String> known) {
        Set<String> mine = new HashSet<>();
        for (Content.Idea i : c.ideas()) for (String p : i.any()) mine.add(String.join(" ", stems(p)));
        String asked = " " + String.join(" ", stems(c.ask + " " + (c.apply == null ? "" : c.apply) + " " + (c.cloze == null ? "" : c.cloze))) + " ";
        List<Content.Term> out = new ArrayList<>();
        for (Content.Term t : content.terms.values()) {
            boolean ours = false;
            for (String name : t.names) {
                String s = String.join(" ", stems(name));
                if (mine.contains(s) || asked.contains(" " + s + " ")) { ours = true; break; }
            }
            if (!ours && hit(u, t.names, known)) out.add(t);
            if (out.size() == 2) break;
        }
        return out;
    }

    /** Does any of these phrasings occur (not negated) in the answer? Typos allowed. */
    static boolean hit(List<String> u, List<String> phrases, Set<String> known) {
        for (String p : phrases) if (find(u, tokens(p), known, new ArrayList<>(), true, true) >= 0) return true;
        return false;
    }

    /**
     * Where the phrase starts in the answer, or -1. Its words must come in order, with at most one extra word
     * between neighbours ("secure base" matches "secure, safe base"). An occurrence right after a negator
     * ("not", "unlike", "rather than"…) doesn't count if always is set, or if terms is set and the phrase is made of
     * course terms; a phrase that is itself negative ("not all") always counts.
     */
    static int find(List<String> u, List<String> p, Set<String> known, List<Fix> fixes, boolean always, boolean terms) {
        if (p.isEmpty()) return -1;
        boolean neg = always;
        if (!neg && terms) { neg = true; for (String w : p) if (!known.contains(norm(w))) { neg = false; break; } }
        boolean negativePhrase = NEGATORS.contains(p.get(0));
        for (int i = 0; i < u.size(); i++) {
            List<Fix> f = new ArrayList<>();
            if (!same(u.get(i), p.get(0), known, f)) continue;
            int j = i, k = 1;
            while (k < p.size()) {
                int found = -1;
                for (int s = j + 1; s <= Math.min(u.size() - 1, j + 2); s++) if (same(u.get(s), p.get(k), known, f)) { found = s; break; }
                if (found < 0) break;
                j = found;
                k++;
            }
            if (k < p.size()) continue;
            if (neg && !negativePhrase && negated(u, i)) continue;
            fixes.addAll(f);
            return i;
        }
        return -1;
    }

    private static boolean negated(List<String> u, int i) {
        for (int b = Math.max(0, i - 2); b < i; b++) if (NEGATORS.contains(u.get(b))) return true;
        if (i >= 2 && (u.get(i - 2).equals("rather") && u.get(i - 1).equals("than")
                || u.get(i - 2).equals("instead") && u.get(i - 1).equals("of"))) return true;
        return false;
    }

    /** Same word after stemming, or a slip of the fingers on a word that isn't itself a course term. */
    static boolean same(String typed, String want, Set<String> known, List<Fix> fixes) {
        String a = norm(typed), b = norm(want);
        if (a.equals(b)) return true;
        if (a.isEmpty() || b.isEmpty() || Character.isDigit(a.charAt(0)) || Character.isDigit(b.charAt(0))) return false;
        if (known.contains(a)) return false;
        if (typo(a, b)) { fixes.add(new Fix(typed, want)); return true; }
        return false;
    }

    /** Up to one slip (a wrong, missing or extra letter, or two letters swapped) per 6 letters, from 5 letters up. */
    static boolean typo(String a, String b) {
        int n = Math.max(a.length(), b.length());
        if (Math.min(a.length(), b.length()) < 5) return false;
        int allowed = n >= 12 ? 2 : 1;
        if (a.charAt(0) != b.charAt(0)) return false;
        return distance(a, b, allowed) <= allowed;
    }

    /** Damerau–Levenshtein distance (optimal string alignment), stopping early past max. */
    static int distance(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
            d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1))
                d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
        return d[a.length()][b.length()];
    }

    /** Lower-cased words; punctuation and apostrophes split words ("child's" → "child", "s"). Digits are kept. */
    public static List<String> tokens(String s) {
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace("’", "'").replaceAll("n't\\b", "nt").replaceAll("[^a-z0-9]+", " ").trim();
        List<String> out = new ArrayList<>();
        if (!t.isEmpty()) out.addAll(Arrays.asList(t.split(" ")));
        return out;
    }

    static List<String> stems(String s) {
        List<String> out = new ArrayList<>();
        for (String t : tokens(s)) out.add(norm(t));
        return out;
    }

    /**
     * A comparison key for one word: -ise → -ize, -our → -or, then common endings dropped
     * (assimilated, assimilates, assimilating → assimilat; studies → study).
     */
    static String norm(String w) {
        if (w.length() <= 3) return w;
        w = w.replaceAll("(?<=..)is(e|ed|es|ing|ation|ations)$", "iz$1").replaceAll("(?<=..)our(s|al)?$", "or$1");
        if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        for (String end : new String[]{"ing", "ed", "es", "s", "e"}) {
            if (w.endsWith(end) && w.length() - end.length() >= 4 && !(end.equals("s") && w.endsWith("ss"))) return w.substring(0, w.length() - end.length());
        }
        return w;
    }
}
