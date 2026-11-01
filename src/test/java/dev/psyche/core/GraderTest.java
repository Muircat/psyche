package dev.psyche.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the offline marker treats real-looking answers. */
class GraderTest {
    private static final Content CONTENT = ContentTest.load();

    private static Grader.Result check(String id, String answer) {
        Content.Challenge c = CONTENT.byId(id);
        assertNotNull(c, id);
        return Grader.check(answer, c, CONTENT);
    }

    @Test
    void acceptsOtherWordings() {
        assertTrue(check("pia_assim", "it's assimilating").ok());
        assertTrue(check("pia_assim", "Assimilated.").ok());
        assertTrue(check("vyg_zpd", "the difference between what a kid can manage on their own and what they can do with an adult helping").ok());
        assertTrue(check("att_def", "a long-lasting emotional bond with a particular caregiver").ok());
    }

    @Test
    void foldsBritishAndAmericanSpelling() {
        assertTrue(check("att_disorg_id", "disorganized").ok());
        assertTrue(check("att_disorg_id", "disorganised").ok());
        assertTrue(check("meth_dv", "the kids' aggressive behavior").ok());
    }

    @Test
    void forgivesTyposWithAWarning() {
        Grader.Result r = check("pia_assim", "assimilaton");
        assertEquals(Grader.Verdict.TYPO, r.verdict());
        assertEquals("assimilation", r.fixes().get(0).meant());
        assertEquals(Grader.Verdict.TYPO, check("vyg_scaff_id", "scafolding").verdict());
    }

    @Test
    void neverTakesOneTermForATypoOfAnother() {
        assertFalse(check("eco_micro", "macrosystem").ok());
        assertFalse(check("eco_macro", "microsystem").ok());
        assertFalse(check("meth_qual", "quantitative").ok());
        assertFalse(check("par_authoritative", "authoritarian").ok());
    }

    @Test
    void explainsKnownMisconceptions() {
        Grader.Result r = check("pia_assim", "accommodation");
        assertFalse(r.ok());
        assertNotNull(r.trap());
        assertTrue(r.trap().why.contains("Accommodation"));
        assertNotNull(check("tom_maxi", "the blue cupboard").trap());
    }

    @Test
    void hedgingBetweenTwoTermsFails() {
        assertFalse(check("pia_assim", "assimilation or accommodation").ok());
        assertFalse(check("meth_cross", "cross-sectional or longitudinal").ok());
    }

    @Test
    void negatedTermsDontCount() {
        assertTrue(check("pia_assim", "assimilation, not accommodation").ok());
        assertFalse(check("pia_assim", "not assimilation").ok());
        assertTrue(check("tom_maxi", "green, because he didn't see it moved to the blue one").ok());
        assertTrue(check("tom_sally", "the basket, because she didn't see it go in the box").ok());
    }

    @Test
    void multiIdeaAnswersNeedEnoughIdeas() {
        Grader.Result r = check("vyg_scaff", "help from an adult");
        assertFalse(r.ok());
        assertEquals(1, r.count());
        assertTrue(check("vyg_scaff", "temporary help from an adult that is gradually withdrawn").ok());
    }

    @Test
    void orderMatters() {
        assertTrue(check("pia_stages", "sensorimotor, preoperational, concrete operational, formal operational").ok());
        assertFalse(check("pia_stages", "preoperational, sensorimotor, concrete operational, formal operational").ok());
        assertTrue(check("eco_levels", "micro meso exo macro chrono").ok());
    }

    @Test
    void namesOtherTermsUsedInAWrongAnswer() {
        Grader.Result r = check("vyg_private_id", "scaffolding");
        assertFalse(r.ok());
        assertTrue(r.mixed().stream().anyMatch(t -> t.id.equals("scaffolding")));
    }

    @Test
    void clozeIsMarkedInContext() {
        Content.Challenge c = CONTENT.byId("intro_def");
        assertTrue(Grader.check("lifespan", c, CONTENT, true).ok());
        assertFalse(Grader.check("body", c, CONTENT, true).ok());
    }
}
