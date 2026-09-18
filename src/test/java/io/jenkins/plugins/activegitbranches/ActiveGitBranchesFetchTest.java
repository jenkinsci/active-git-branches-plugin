package io.jenkins.plugins.activegitbranches;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fetches branches from a local repository through both fetch modes.
 */
@WithJenkins
class ActiveGitBranchesFetchTest {

    @TempDir
    File upstreamDir;

    @Test
    void fetchesBranchesInBothModes(JenkinsRule j) throws Exception {
        try (Git upstream = Git.init().setDirectory(upstreamDir).setInitialBranch("main").call()) {
            commit(upstream, "initial", 1_000_000L);
            upstream.branchCreate().setName("aaa-old").call();
            upstream.checkout().setCreateBranch(true).setName("zzz-new").call();
            commit(upstream, "newer", 3_000_000L);
            upstream.checkout().setName("main").call();
            commit(upstream, "middle", 2_000_000L);
        }
        String url = upstreamDir.toURI().toString();

        // Quick fetch: alphabetical
        List<ActiveGitBranchesParameterDefinition.BranchInfo> quick = ActiveGitBranchesParameterDefinition
                .fetchAllBranches(new ActiveGitBranchesParameterDefinition.CacheKey(url, null, true));
        assertEquals(List.of("aaa-old", "main", "zzz-new"), names(quick));

        // Cache repository: newest commit first; run twice to cover the incremental fetch
        ActiveGitBranchesParameterDefinition.CacheKey key =
                new ActiveGitBranchesParameterDefinition.CacheKey(url, null, false);
        assertEquals(List.of("zzz-new", "main", "aaa-old"), names(ActiveGitBranchesParameterDefinition.fetchAllBranches(key)));

        try (Git upstream = Git.open(upstreamDir)) {
            upstream.checkout().setName("aaa-old").call();
            commit(upstream, "newest", 4_000_000L);
            upstream.checkout().setName("main").call();
            upstream.branchDelete().setBranchNames("zzz-new").setForce(true).call();
        }
        assertEquals(List.of("aaa-old", "main"), names(ActiveGitBranchesParameterDefinition.fetchAllBranches(key)));
    }

    private static void commit(Git git, String message, long epochSeconds) throws Exception {
        PersonIdent ident = new PersonIdent("test", "test@example.com", new Date(epochSeconds * 1000L), java.util.TimeZone.getTimeZone("UTC"));
        git.commit().setMessage(message).setAllowEmpty(true).setAuthor(ident).setCommitter(ident).setSign(false).call();
    }

    private static List<String> names(List<ActiveGitBranchesParameterDefinition.BranchInfo> branches) {
        return branches.stream().map(ActiveGitBranchesParameterDefinition.BranchInfo::getName).toList();
    }
}
