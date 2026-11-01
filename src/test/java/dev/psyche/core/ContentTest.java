package dev.psyche.core;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** Checks the built-in course content against its own grader, so every question can actually be answered. */
class ContentTest {
    static Content load() {
        Content c = new Content();
        try (var r = new InputStreamReader(ContentTest.class.getResourceAsStream("/assets/psyche/content.json"), StandardCharsets.UTF_8)) {
            c.load(r);
        } catch (Exception e) { throw new RuntimeException(e); }
        return c;
    }

    @Test
    void contentLoads() {
        Content c = load();
        assertTrue(c.challenges.size() >= 200, "expected the full question bank, got " + c.challenges.size());
        assertTrue(c.topics.size() >= 15);
    }

    @Test
    void everyChallengeIsWellFormed() {
        Content content = load();
        List<String> problems = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Content.Challenge c : content.challenges) {
            if (!ids.add(c.id)) problems.add(c.id + ": duplicate id");
            if (!content.topics.containsKey(c.topic)) problems.add(c.id + ": unknown topic " + c.topic);
            if (c.level < 1 || c.level > 3) problems.add(c.id + ": level must be 1-3");
            if (c.ideas().isEmpty()) problems.add(c.id + ": no key ideas");
            for (String n : c.next) if (content.byId(n) == null) problems.add(c.id + ": unknown follow-up " + n);
            if (c.npc != null && content.triggers(c).stream().noneMatch(t -> t.startsWith("trade:"))) problems.add(c.id + ": villager line without a trade trigger");
            if (content.triggers(c).isEmpty()) problems.add(c.id + ": no gameplay trigger");
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    @Test
    void modelAnswersPass() {
        Content content = load();
        List<String> problems = new ArrayList<>();
        for (Content.Challenge c : content.challenges) {
            Grader.Result r = Grader.check(c.answer, c, content);
            if (r.verdict() != Grader.Verdict.EXACT)
                problems.add(c.id + ": model answer marked " + r.verdict() + " (" + r.count() + "/" + r.needed() + (r.trap() != null ? ", trap: " + r.trap().why : "") + ")");
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    @Test
    void clozeBlanksPassAndNeedTheBlank() {
        Content content = load();
        List<String> problems = new ArrayList<>();
        for (Content.Challenge c : content.challenges) {
            if (!c.hasCloze()) continue;
            if (Grader.check(c.blank(), c, content, true).verdict() != Grader.Verdict.EXACT) problems.add(c.id + ": cloze blank '" + c.blank() + "' doesn't pass");
            if (Grader.check("something", c, content, true).ok()) problems.add(c.id + ": cloze passes without the blank");
        }
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    @Test
    void nonAnswersFail() {
        Content content = load();
        List<String> problems = new ArrayList<>();
        for (Content.Challenge c : content.challenges)
            for (String junk : List.of("I don't know", "idk", "something about children"))
                if (Grader.check(junk, c, content).ok()) problems.add(c.id + ": '" + junk + "' passes");
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }

    @Test
    void everyTopicHasQuestionsAtEachLevel() {
        Content content = load();
        List<String> problems = new ArrayList<>();
        for (String t : content.topics.keySet())
            if (content.challenges.stream().noneMatch(c -> c.topic.equals(t) && c.level == 1 && c.npc == null)) problems.add(t + ": no level-1 question");
        if (!problems.isEmpty()) fail(String.join("\n", problems));
    }
}
