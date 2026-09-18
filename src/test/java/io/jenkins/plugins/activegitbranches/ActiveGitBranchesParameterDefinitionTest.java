package io.jenkins.plugins.activegitbranches;

import hudson.model.ParameterValue;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ActiveGitBranchesParameterDefinition.
 */
public class ActiveGitBranchesParameterDefinitionTest {

    @Test
    public void testParameterCreation() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                "Select a branch"
        );

        assertEquals("BRANCH", param.getName());
        assertEquals("https://github.com/jenkinsci/jenkins.git", param.getRepositoryUrl());
        assertEquals(10, param.getMaxBranchCount());
        assertEquals("Select a branch", param.getDescription());
    }

    @Test
    public void testCredentialsIdSetter() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        assertNull(param.getCredentialsId());
        param.setCredentialsId("my-credentials");
        assertEquals("my-credentials", param.getCredentialsId());
    }

    @Test
    public void testBranchFilterSetter() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        assertNull(param.getBranchFilter());
        param.setBranchFilter("feature/.*");
        assertEquals("feature/.*", param.getBranchFilter());
    }

    @Test
    public void testDefaultValueSetter() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        assertNull(param.getDefaultValue());
        param.setDefaultValue("main");
        assertEquals("main", param.getDefaultValue());
    }

    @Test
    public void testMaxBranchCountDefaultsToTenWhenZero() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                0,
                null
        );

        assertEquals(10, param.getMaxBranchCount());
    }

    @Test
    public void testMaxBranchCountDefaultsToTenWhenNegative() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                -5,
                null
        );

        assertEquals(10, param.getMaxBranchCount());
    }

    @Test
    public void testDescriptorDisplayName() {
        ActiveGitBranchesParameterDefinition.DescriptorImpl descriptor = 
                new ActiveGitBranchesParameterDefinition.DescriptorImpl();
        
        assertEquals("Active Git Branches Parameter", descriptor.getDisplayName());
    }

    @Test
    public void testDefaultValueDoesNotUseRemoteFillMethod() throws Exception {
        assertThrows(NoSuchMethodException.class, () ->
                ActiveGitBranchesParameterDefinition.DescriptorImpl.class.getMethod(
                        "doFillDefaultValueItems",
                        String.class,
                        String.class,
                        int.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class));
    }

    @Test
    public void testTestConnectionRequiresPost() throws Exception {
        assertNotNull(ActiveGitBranchesParameterDefinition.DescriptorImpl.class
                .getMethod("doTestConnection",
                        hudson.model.Item.class,
                        String.class,
                        String.class,
                        int.class,
                        String.class,
                        String.class,
                        String.class,
                        boolean.class)
                .getAnnotation(RequirePOST.class));
    }

    @Test
    public void testValidateRepositoryUrl() {
        ActiveGitBranchesParameterDefinition.DescriptorImpl descriptor = 
                new ActiveGitBranchesParameterDefinition.DescriptorImpl();

        // Empty URL should fail
        FormValidation validation = descriptor.doCheckRepositoryUrl("");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);

        // Null URL should fail
        validation = descriptor.doCheckRepositoryUrl(null);
        assertEquals(FormValidation.Kind.ERROR, validation.kind);

        // Valid HTTPS URL should pass
        validation = descriptor.doCheckRepositoryUrl("https://github.com/user/repo.git");
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Valid SSH URL should pass
        validation = descriptor.doCheckRepositoryUrl("git@github.com:user/repo.git");
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Invalid URL should warn
        validation = descriptor.doCheckRepositoryUrl("ftp://invalid.url");
        assertEquals(FormValidation.Kind.WARNING, validation.kind);
    }

    @Test
    public void testValidateMaxBranchCount() {
        ActiveGitBranchesParameterDefinition.DescriptorImpl descriptor = 
                new ActiveGitBranchesParameterDefinition.DescriptorImpl();

        // Empty should fail
        FormValidation validation = descriptor.doCheckMaxBranchCount("");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);

        // Zero should fail
        validation = descriptor.doCheckMaxBranchCount("0");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);

        // Negative should fail
        validation = descriptor.doCheckMaxBranchCount("-1");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);

        // Valid number should pass
        validation = descriptor.doCheckMaxBranchCount("10");
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Large number should warn
        validation = descriptor.doCheckMaxBranchCount("150");
        assertEquals(FormValidation.Kind.WARNING, validation.kind);

        // Non-numeric should fail
        validation = descriptor.doCheckMaxBranchCount("abc");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);
    }

    @Test
    public void testValidateBranchFilter() {
        ActiveGitBranchesParameterDefinition.DescriptorImpl descriptor = 
                new ActiveGitBranchesParameterDefinition.DescriptorImpl();

        // Empty filter should be OK
        FormValidation validation = descriptor.doCheckBranchFilter("");
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Null filter should be OK
        validation = descriptor.doCheckBranchFilter(null);
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Valid regex should be OK
        validation = descriptor.doCheckBranchFilter("feature/.*");
        assertEquals(FormValidation.Kind.OK, validation.kind);

        // Invalid regex should fail
        validation = descriptor.doCheckBranchFilter("[invalid");
        assertEquals(FormValidation.Kind.ERROR, validation.kind);
    }

    @Test
    public void testBranchInfo() {
        ActiveGitBranchesParameterDefinition.BranchInfo info = 
                new ActiveGitBranchesParameterDefinition.BranchInfo("feature/test", 1234567890000L);

        assertEquals("feature/test", info.getName());
        assertEquals(1234567890000L, info.getCommitTime());
    }

    @Test
    public void testParameterValueCreation() {
        ActiveGitBranchesParameterValue value = 
                new ActiveGitBranchesParameterValue("BRANCH", "main");

        assertEquals("BRANCH", value.getName());
        assertEquals("main", value.getValue());
    }

    @Test
    public void testParameterValueWithDescription() {
        ActiveGitBranchesParameterValue value = 
                new ActiveGitBranchesParameterValue("BRANCH", "main", "Main branch");

        assertEquals("BRANCH", value.getName());
        assertEquals("main", value.getValue());
        assertEquals("Main branch", value.getDescription());
    }

    @Test
    public void testParameterValueToString() {
        ActiveGitBranchesParameterValue value = 
                new ActiveGitBranchesParameterValue("BRANCH", "develop");

        String str = value.toString();
        assertTrue(str.contains("ActiveGitBranchesParameterValue"));
        assertTrue(str.contains("BRANCH"));
        assertTrue(str.contains("develop"));
    }

    @Test
    public void testExcludeBranchesSetter() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        assertNull(param.getExcludeBranches());
        param.setExcludeBranches("(master|main)");
        assertEquals("(master|main)", param.getExcludeBranches());
    }

    @Test
    public void testAlwaysIncludeBranchesSetter() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        assertNull(param.getAlwaysIncludeBranches());
        param.setAlwaysIncludeBranches("develop|release/.*");
        assertEquals("develop|release/.*", param.getAlwaysIncludeBranches());
    }

    @Test
    public void testValidateExcludeBranches() {
        ActiveGitBranchesParameterDefinition.DescriptorImpl descriptor = 
                new ActiveGitBranchesParameterDefinition.DescriptorImpl();

        // Empty should be OK
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExcludeBranches("").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExcludeBranches(null).kind);

        // Valid regex should pass
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExcludeBranches("(master|main)").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckExcludeBranches("release/.*").kind);

        // Invalid regex should fail
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckExcludeBranches("[invalid").kind);
    }

    @Test
    public void testMatchesExcludeBranches() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );

        // When null or empty, never matches
        assertFalse(param.matchesExcludeBranches("master"));
        assertFalse(param.matchesExcludeBranches(null));

        param.setExcludeBranches("(master|release/.*)");
        assertTrue(param.matchesExcludeBranches("master"));
        assertTrue(param.matchesExcludeBranches("release/1.0.0"));
        assertFalse(param.matchesExcludeBranches("develop"));
        assertFalse(param.matchesExcludeBranches("main"));
        assertFalse(param.matchesExcludeBranches(""));
        assertFalse(param.matchesExcludeBranches(null));

        // Invalid regex should not throw exception, should return false
        param.setExcludeBranches("[invalid");
        assertFalse(param.matchesExcludeBranches("master"));
    }

    @Test
    public void testBranchInfoDisabledProperty() {
        ActiveGitBranchesParameterDefinition.BranchInfo normal = 
                new ActiveGitBranchesParameterDefinition.BranchInfo("develop", 1000L);
        assertFalse(normal.isDisabled());

        ActiveGitBranchesParameterDefinition.BranchInfo disabled = 
                new ActiveGitBranchesParameterDefinition.BranchInfo("master", 2000L, true);
        assertTrue(disabled.isDisabled());
    }

    @Test
    public void testExcludeBranchesSecurityBlockInCreateValue() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        );
        param.setExcludeBranches("(master|main)");
        param.setAllowCustomBranch(true);

        // 1. Direct forbidden branch
        JSONObject jo = new JSONObject();
        jo.put("value", "master");
        ParameterValue val = param.createValue((StaplerRequest2) null, jo);
        assertEquals("", ((ActiveGitBranchesParameterValue) val).getValue());

        // 2. Custom input attempting forbidden branch
        JSONObject joCustom = new JSONObject();
        joCustom.put("value", "__custom__");
        joCustom.put("customValue", "main");
        ParameterValue valCustom = param.createValue((StaplerRequest2) null, joCustom);
        assertEquals("", ((ActiveGitBranchesParameterValue) valCustom).getValue());

        // 3. Allowed branch
        JSONObject joAllowed = new JSONObject();
        joAllowed.put("value", "feature/awesome");
        ParameterValue valAllowed = param.createValue((StaplerRequest2) null, joAllowed);
        assertEquals("feature/awesome", ((ActiveGitBranchesParameterValue) valAllowed).getValue());
    }

    @Test
    public void testEffectiveDefaultValue() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH",
                "https://github.com/jenkinsci/jenkins.git",
                10,
                null
        ) {
            @Override
            public java.util.List<ActiveGitBranchesParameterDefinition.BranchInfo> fetchBranches() {
                return java.util.Collections.singletonList(
                        new ActiveGitBranchesParameterDefinition.BranchInfo("develop", 1000L));
            }
        };
        param.setDefaultValue("feature/login");
        assertEquals("feature/login", param.getEffectiveDefaultValue());

        // When defaultValue is excluded, it should not return the excluded defaultValue
        param.setExcludeBranches("feature/.*");
        assertEquals("develop", param.getEffectiveDefaultValue());
    }

    @Test
    public void testFilterAndLimit() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH", "https://github.com/org/repo.git", 3, null);
        param.setBranchFilter("feature/.*");
        param.setAlwaysIncludeBranches("main");
        param.setExcludeBranches("main");

        java.util.List<ActiveGitBranchesParameterDefinition.BranchInfo> all = java.util.List.of(
                new ActiveGitBranchesParameterDefinition.BranchInfo("feature/a", 500L),
                new ActiveGitBranchesParameterDefinition.BranchInfo("bugfix/x", 400L),
                new ActiveGitBranchesParameterDefinition.BranchInfo("feature/b", 300L),
                new ActiveGitBranchesParameterDefinition.BranchInfo("feature/c", 200L),
                new ActiveGitBranchesParameterDefinition.BranchInfo("main", 100L));

        java.util.List<ActiveGitBranchesParameterDefinition.BranchInfo> result = param.filterAndLimit(all);

        // main is always included (first) and disabled; the filter drops bugfix/x; the limit drops feature/c
        assertEquals(java.util.List.of("main", "feature/a", "feature/b"),
                result.stream().map(ActiveGitBranchesParameterDefinition.BranchInfo::getName).toList());
        assertTrue(result.get(0).isDisabled());
        assertFalse(result.get(1).isDisabled());
    }

    @Test
    public void testFilterAndLimitKeepsSortOrderWhenUnderLimit() {
        ActiveGitBranchesParameterDefinition param = new ActiveGitBranchesParameterDefinition(
                "BRANCH", "https://github.com/org/repo.git", 10, null);
        param.setAlwaysIncludeBranches("main");

        java.util.List<ActiveGitBranchesParameterDefinition.BranchInfo> result = param.filterAndLimit(java.util.List.of(
                new ActiveGitBranchesParameterDefinition.BranchInfo("feature/a", 500L),
                new ActiveGitBranchesParameterDefinition.BranchInfo("main", 100L)));

        assertEquals(java.util.List.of("feature/a", "main"),
                result.stream().map(ActiveGitBranchesParameterDefinition.BranchInfo::getName).toList());
    }

    @Test
    public void testCacheKeyIgnoresFilters() {
        // Parameters that only differ in filters share one cached fetch
        assertEquals(
                new ActiveGitBranchesParameterDefinition.CacheKey("https://github.com/org/repo.git", "creds", true),
                new ActiveGitBranchesParameterDefinition.CacheKey("https://github.com/org/repo.git", "creds", true));
        assertNotEquals(
                new ActiveGitBranchesParameterDefinition.CacheKey("https://github.com/org/repo.git", "creds", true),
                new ActiveGitBranchesParameterDefinition.CacheKey("https://github.com/org/repo.git", "creds", false));
    }
}
