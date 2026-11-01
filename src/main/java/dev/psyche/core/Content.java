package dev.psyche.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.*;
import java.util.regex.Pattern;

/** The curated library: course topics, a glossary of key terms, and hand-written challenges, loaded from JSON packs. */
public final class Content {
    private static final Gson GSON = new Gson();

    /** A course topic. on: gameplay triggers its challenges answer to, unless a challenge sets its own. */
    public record Topic(String id, String name, String rule, String src, List<String> on) {}

    /** A glossary entry: the ways of writing the term and a one-line definition. */
    public static final class Term {
        public String id, def;
        public List<String> names = List.of();
        public String name() { return names.isEmpty() ? id : names.get(0); }
    }

    /** One key idea an answer must contain: a label (shown once it's been said) and every accepted phrasing. */
    public record Idea(String idea, List<String> any) {}

    /**
     * A typical wrong answer and why it's wrong. any: phrasings that give it away. veto: the answer counts as
     * wrong even if it also contains the right idea (stops hedging like "assimilation or accommodation").
     * skill tags the habit to practise.
     */
    public static final class Trap {
        public List<String> any;
        public String why, skill;
        public boolean veto;
    }

    /**
     * One challenge, always typed.
     * ask: the recall question. cloze: a sentence with the answer in [brackets], for "complete" mode.
     * apply: a scenario to diagnose, for the top of the ladder. need: the key ideas, each a list of phrasings
     * (or {"idea": label, "any": [...]}); min: how many of them are enough (default all); order: they must come in order.
     * answer: the model answer shown afterwards; why: the explanation; src: where it's from in the course.
     */
    public static final class Challenge {
        public String id, topic, ask, cloze, apply, answer, hint = "", why = "", src, npc;
        public int level = 1, min;
        public boolean order;
        public List<String> on = List.of(), extra = List.of(), next = List.of();
        public List<JsonElement> need = List.of();
        public List<Trap> traps = List.of();
        private transient List<Idea> ideas;
        private transient String prefix, blank, suffix;

        void init() {
            ideas = new ArrayList<>();
            for (JsonElement e : need) {
                if (e.isJsonArray()) {
                    List<String> any = new ArrayList<>();
                    e.getAsJsonArray().forEach(x -> any.add(x.getAsString()));
                    if (!any.isEmpty()) ideas.add(new Idea(any.get(0), any));
                } else if (e.isJsonPrimitive()) ideas.add(new Idea(e.getAsString(), List.of(e.getAsString())));
                else {
                    JsonObject o = e.getAsJsonObject();
                    List<String> any = new ArrayList<>();
                    o.getAsJsonArray("any").forEach(x -> any.add(x.getAsString()));
                    ideas.add(new Idea(o.has("idea") ? o.get("idea").getAsString() : any.get(0), any));
                }
            }
            if (cloze != null) {
                int i = cloze.indexOf('['), j = cloze.indexOf(']');
                if (i >= 0 && j > i) { prefix = cloze.substring(0, i); blank = cloze.substring(i + 1, j); suffix = cloze.substring(j + 1); }
                else cloze = null;
            }
            if (answer == null) answer = ideas.isEmpty() ? "" : ideas.get(0).any().get(0);
        }

        public List<Idea> ideas() { return ideas; }
        public int needed() { return min > 0 ? Math.min(min, ideas.size()) : ideas.size(); }
        public boolean hasCloze() { return cloze != null; }
        public boolean hasApply() { return apply != null && !apply.isBlank(); }
        public String prefix() { return prefix; }
        public String blank() { return blank; }
        public String suffix() { return suffix; }
        /** Short answer = one idea; the hint ladder shows its first letters instead of a list of ideas. */
        public boolean shortAnswer() { return ideas.size() == 1; }

        /** Skill tags this challenge exercises, from its traps. */
        public Set<String> skills() {
            Set<String> out = new HashSet<>();
            for (Trap t : traps) if (t.skill != null) out.add(t.skill);
            return out;
        }

        /** Word tiles for the word bank: each idea's main phrasing plus distractors, shuffled. */
        public List<String> tiles(Random rnd) {
            List<String> out = new ArrayList<>();
            for (Idea i : ideas) out.add(i.any().get(0));
            int real = out.size();
            out.addAll(extra);
            List<String> first = new ArrayList<>(out);
            for (int n = 0; n < 8 && out.size() > 1 && out.subList(0, real).equals(first.subList(0, real)); n++) Collections.shuffle(out, rnd);
            return out;
        }
    }

    public final Map<String, Topic> topics = new LinkedHashMap<>();
    public final Map<String, Term> terms = new LinkedHashMap<>();
    public final List<Challenge> challenges = new ArrayList<>();
    private Set<String> known;

    /** Loads a pack. Later packs may add topics and terms, or replace challenges by id. */
    public void load(Reader r) {
        JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
        if (o.has("topics")) for (var e : o.getAsJsonObject("topics").entrySet()) {
            JsonObject t = e.getValue().getAsJsonObject();
            List<String> on = new ArrayList<>();
            if (t.has("on")) t.getAsJsonArray("on").forEach(x -> on.add(x.getAsString()));
            topics.put(e.getKey(), new Topic(e.getKey(), t.get("name").getAsString(), t.get("rule").getAsString(),
                    t.has("src") ? t.get("src").getAsString() : "", on));
        }
        if (o.has("terms")) for (var e : o.getAsJsonObject("terms").entrySet()) {
            Term t = GSON.fromJson(e.getValue(), Term.class);
            t.id = e.getKey();
            if (t.names.isEmpty()) t.names = List.of(e.getKey());
            terms.put(e.getKey(), t);
        }
        if (o.has("challenges")) for (JsonElement e : o.getAsJsonArray("challenges")) {
            Challenge c = GSON.fromJson(e, Challenge.class);
            if (c.id == null || c.ask == null || c.need == null || c.need.isEmpty()) continue;
            if (c.traps == null) c.traps = List.of();
            c.init();
            challenges.removeIf(x -> x.id.equals(c.id));
            challenges.add(c);
        }
        known = null;
    }

    /** Every word of every glossary term: these are never mistaken for typos of one another. */
    public Set<String> known() {
        if (known == null) {
            Set<String> k = new HashSet<>();
            for (Term t : terms.values()) for (String n : t.names) k.addAll(Grader.stems(n));
            known = k;
        }
        return known;
    }

    /** The triggers a challenge answers to: its own, or else its topic's. */
    public List<String> triggers(Challenge c) {
        if (!c.on.isEmpty()) return c.on;
        Topic t = topics.get(c.topic);
        return t == null ? List.of() : t.on();
    }

    /**
     * Picks a challenge for a gameplay trigger (e.g. "kill:zombie"), or for practice when trigger is null
     * (optionally limited to one topic). Weak, unseen and recently-missed challenges are favoured.
     */
    public Challenge pick(String trigger, String topic, Profile p, Random rnd) {
        List<Challenge> pool = challenges.stream().filter(c -> trigger == null
                ? c.npc == null && (topic == null || c.topic.equals(topic))
                : (c.npc != null) == trigger.startsWith("trade:") && triggers(c).stream().anyMatch(g -> glob(g, trigger))).toList();
        double[] w = new double[pool.size()];
        double total = 0;
        for (int i = 0; i < w.length; i++) total += w[i] = p.weight(pool.get(i));
        if (total <= 0) return null;
        double roll = rnd.nextDouble() * total;
        for (int i = 0; i < w.length; i++) if (w[i] > 0 && (roll -= w[i]) <= 0) return pool.get(i);
        return pool.isEmpty() ? null : pool.get(pool.size() - 1);
    }

    /** A follow-up that builds on c, if c has any the learner is ready for. */
    public Challenge followUp(Challenge c, Profile p, Random rnd) {
        List<Challenge> pool = new ArrayList<>();
        for (String id : c.next) { Challenge n = byId(id); if (n != null && n != c && p.weight(n) > 0) pool.add(n); }
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size()));
    }

    public Challenge byId(String id) {
        for (Challenge c : challenges) if (c.id.equals(id)) return c;
        return null;
    }

    static boolean glob(String pattern, String key) {
        return pattern.equals(key) || pattern.contains("*") && key.matches(Pattern.quote(pattern).replace("*", "\\E.*\\Q"));
    }
}
