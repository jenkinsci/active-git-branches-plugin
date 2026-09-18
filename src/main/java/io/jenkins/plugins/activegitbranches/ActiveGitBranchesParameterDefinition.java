package io.jenkins.plugins.activegitbranches;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.Item;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.LogTaskListener;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Active Git Branches Parameter Definition.
 *
 * This parameter plugin dynamically fetches Git branches from a remote repository,
 * sorts them by commit date (descending), and limits the display to the top N branches.
 */
public class ActiveGitBranchesParameterDefinition extends ParameterDefinition {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ActiveGitBranchesParameterDefinition.class.getName());

    private static final String REMOTE_BRANCH_PREFIX = "refs/remotes/origin/";

    /**
     * Minimum age of a cached branch list before a page load triggers a
     * background refresh. Bounds how often the controller talks to the Git
     * server, no matter how often the Build with Parameters page is opened.
     */
    static final long REFRESH_INTERVAL_SECONDS = Math.max(1L, SystemProperties.getLong(
            ActiveGitBranchesParameterDefinition.class.getName() + ".refreshIntervalSeconds", 60L));

    // ---------------------------------------------------------------------
    // Stale-while-revalidate cache, keyed by repository + credentials + fetch
    // mode only, so parameters that differ just in filters share one fetch.
    // A hit older than REFRESH_INTERVAL_SECONDS is returned immediately while
    // Caffeine reloads it on REFRESH_EXECUTOR; a miss loads synchronously.
    // Entries not accessed for a day are evicted.
    // ---------------------------------------------------------------------
    private static final ExecutorService REFRESH_EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private int counter = 0;
        @Override
        public synchronized Thread newThread(Runnable r) {
            Thread t = new Thread(r, "active-git-branches-refresh-" + (++counter));
            t.setDaemon(true);
            return t;
        }
    });
    private static final LoadingCache<CacheKey, CacheEntry> CACHE = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofDays(1))
            .refreshAfterWrite(Duration.ofSeconds(REFRESH_INTERVAL_SECONDS))
            .executor(REFRESH_EXECUTOR)
            .build(new BranchLoader());

    /** Serializes fetches into the same persistent cache repository. */
    private static final ConcurrentMap<String, Object> REPO_LOCKS = new ConcurrentHashMap<>();

    private final String repositoryUrl;
    private String credentialsId;
    private final int maxBranchCount;
    private String branchFilter;
    private String alwaysIncludeBranches;
    private String excludeBranches;
    private String defaultValue;
    private boolean useQuickFetch = true;
    private boolean allowCustomBranch = false;

    /**
     * Sentinel option value sent by index.jelly when user picks "Custom..." entry.
     * The actual branch name is then read from the customValue text input.
     */
    static final String CUSTOM_BRANCH_SENTINEL = "__custom__";

    @DataBoundConstructor
    public ActiveGitBranchesParameterDefinition(String name, String repositoryUrl, int maxBranchCount, String description) {
        super(name, description);
        this.repositoryUrl = repositoryUrl;
        this.maxBranchCount = maxBranchCount > 0 ? maxBranchCount : 10;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public int getMaxBranchCount() {
        return maxBranchCount;
    }

    public String getBranchFilter() {
        return branchFilter;
    }

    @DataBoundSetter
    public void setBranchFilter(String branchFilter) {
        this.branchFilter = branchFilter;
    }

    public String getAlwaysIncludeBranches() {
        return alwaysIncludeBranches;
    }

    @DataBoundSetter
    public void setAlwaysIncludeBranches(String alwaysIncludeBranches) {
        this.alwaysIncludeBranches = alwaysIncludeBranches;
    }

    public String getExcludeBranches() {
        return excludeBranches;
    }

    @DataBoundSetter
    public void setExcludeBranches(String excludeBranches) {
        this.excludeBranches = excludeBranches;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @DataBoundSetter
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isUseQuickFetch() {
        return useQuickFetch;
    }

    @DataBoundSetter
    public void setUseQuickFetch(boolean useQuickFetch) {
        this.useQuickFetch = useQuickFetch;
    }

    public boolean isAllowCustomBranch() {
        return allowCustomBranch;
    }

    @DataBoundSetter
    public void setAllowCustomBranch(boolean allowCustomBranch) {
        this.allowCustomBranch = allowCustomBranch;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req, JSONObject jo) {
        String value = jo.optString("value", "");
        if (allowCustomBranch && CUSTOM_BRANCH_SENTINEL.equals(value)) {
            value = jo.optString("customValue", "");
        }
        String sanitized = sanitizeBranchName(value);
        if (matchesExcludeBranches(sanitized)) {
            LOGGER.warning("Rejected branch name because it is excluded: " + sanitized);
            sanitized = "";
        }
        return new ActiveGitBranchesParameterValue(getName(), sanitized, getDescription());
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        String[] values = req.getParameterValues(getName());
        if (values != null && values.length > 0) {
            String value = values[0];
            if (allowCustomBranch && CUSTOM_BRANCH_SENTINEL.equals(value)) {
                String[] customValues = req.getParameterValues("customValue");
                value = (customValues != null && customValues.length > 0) ? customValues[0] : "";
            }
            String sanitized = sanitizeBranchName(value);
            if (matchesExcludeBranches(sanitized)) {
                LOGGER.warning("Rejected branch name because it is excluded: " + sanitized);
                sanitized = "";
            }
            return new ActiveGitBranchesParameterValue(getName(), sanitized, getDescription());
        }
        return getDefaultParameterValue();
    }

    /**
     * Trim whitespace and reject characters that have no business appearing in a git ref name.
     * Returns "" if the input is null/blank or contains forbidden characters; the form layer
     * is expected to surface this back to the user via getDefaultParameterValue() fallback,
     * and the build itself will fail fast if an empty branch is passed downstream.
     */
    static String sanitizeBranchName(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || CUSTOM_BRANCH_SENTINEL.equals(trimmed)) {
            return "";
        }
        // Block obvious injection / malformed ref names (see git-check-ref-format).
        if (trimmed.startsWith("-")
                || trimmed.contains("..")
                || trimmed.contains(" ")
                || trimmed.contains("\t")
                || trimmed.contains("\n")
                || trimmed.contains("\r")
                || trimmed.contains("\\")
                || trimmed.contains("~")
                || trimmed.contains("^")
                || trimmed.contains(":")
                || trimmed.contains("?")
                || trimmed.contains("*")
                || trimmed.contains("[")) {
            LOGGER.warning("Rejected custom branch name with invalid characters: " + trimmed);
            return "";
        }
        return trimmed;
    }

    @Override
    public ParameterValue getDefaultParameterValue() {
        List<BranchInfo> branches = fetchBranches();
        String value = defaultValue;
        if (value != null && matchesExcludeBranches(value)) {
            value = null; // Do not default to a disabled branch
        }
        if ((value == null || value.isEmpty()) && !branches.isEmpty()) {
            for (BranchInfo b : branches) {
                if (!b.isDisabled()) {
                    value = b.getName();
                    break;
                }
            }
            if (value == null) {
                value = branches.get(0).getName();
            }
        }
        return new ActiveGitBranchesParameterValue(getName(), value != null ? value : "", getDescription());
    }

    /**
     * Resolves the effective default branch name to be pre-selected in the UI.
     * Skips any excluded/disabled branch and falls back to the first available active branch.
     */
    public String getEffectiveDefaultValue() {
        ParameterValue pv = getDefaultParameterValue();
        if (pv instanceof ActiveGitBranchesParameterValue) {
            return ((ActiveGitBranchesParameterValue) pv).getValue();
        }
        return defaultValue != null ? defaultValue : "";
    }

    /**
     * Returns the branch list shown to the user, filtered and limited
     * according to this parameter's configuration. Uses a stale-while-revalidate
     * cache:
     * <ul>
     *   <li><b>Cache hit:</b> return the cached snapshot immediately; if it is
     *       older than {@link #REFRESH_INTERVAL_SECONDS}, reload it in the
     *       background so subsequent calls see fresher data.</li>
     *   <li><b>Cache miss (cold start / first call after restart):</b> fetch
     *       synchronously; on failure return an empty list and leave the cache
     *       empty so the next call retries.</li>
     * </ul>
     * Background refresh failures keep the existing cached snapshot intact and
     * only flip {@code lastRefreshFailed} so the UI can warn the user.
     */
    public List<BranchInfo> fetchBranches() {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return filterAndLimit(CACHE.get(buildCacheKey()).branches());
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Failed to fetch branches from repository: " + repositoryUrl, e);
            return new ArrayList<>();
        }
    }

    private CacheKey buildCacheKey() {
        return new CacheKey(repositoryUrl, credentialsId, useQuickFetch);
    }

    /**
     * Human-readable age of the cached branch list (e.g. "5 seconds ago",
     * "2 minutes ago"), or {@code null} if the cache is empty. Exposed for
     * the Jelly view to render a "fetched X ago" hint under the dropdown.
     */
    public String getCacheFetchedAgoText() {
        CacheEntry entry = CACHE.getIfPresent(buildCacheKey());
        if (entry == null) {
            return null;
        }
        return formatAge(System.currentTimeMillis() - entry.fetchedAt());
    }

    /**
     * Whether the most recent background refresh failed; used by the Jelly
     * view to show a small warning next to the freshness hint.
     */
    public boolean isLastRefreshFailed() {
        CacheEntry entry = CACHE.getIfPresent(buildCacheKey());
        return entry != null && entry.lastRefreshFailed();
    }

    private static String formatAge(long ageMs) {
        if (ageMs < 1000L) return "just now";
        long seconds = ageMs / 1000L;
        if (seconds < 60) return seconds + (seconds == 1 ? " second ago" : " seconds ago");
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        long hours = minutes / 60;
        if (hours < 24) return hours + (hours == 1 ? " hour ago" : " hours ago");
        long days = hours / 24;
        return days + (days == 1 ? " day ago" : " days ago");
    }

    /**
     * Fetches the unfiltered branch list for a cache key, bypassing the cache.
     * <ul>
     *   <li>Quick fetch: {@code ls-remote}, fast, sorted alphabetically.</li>
     *   <li>Otherwise: shallow fetch of the branch tips into a persistent
     *       repository on the controller, sorted by commit date. Only changed
     *       tips are transferred after the first fetch.</li>
     * </ul>
     */
    static List<BranchInfo> fetchAllBranches(CacheKey key) throws IOException, InterruptedException {
        if (key.repositoryUrl() == null || key.repositoryUrl().isBlank()) {
            throw new IOException("Repository URL is not configured");
        }
        return key.useQuickFetch() ? fetchBranchesQuick(key) : fetchBranchesWithCacheRepository(key);
    }

    /**
     * Quick fetch using git ls-remote (no clone required).
     * This is much faster but doesn't provide commit timestamps for sorting.
     * Branches are sorted alphabetically instead.
     */
    private static List<BranchInfo> fetchBranchesQuick(CacheKey key) throws IOException, InterruptedException {
        List<BranchInfo> branchInfos = new ArrayList<>();
        File tempDir = Files.createTempDirectory("jenkins-git-branches-").toFile();
        try {
            GitClient git = createGitClient(tempDir, key);
            Map<String, ObjectId> remoteRefs = git.getRemoteReferences(key.repositoryUrl(), null, true, false);
            for (String refName : remoteRefs.keySet()) {
                if (refName.startsWith("refs/heads/")) {
                    // ls-remote doesn't provide commit times
                    branchInfos.add(new BranchInfo(refName.substring("refs/heads/".length()), 0L));
                }
            }
        } finally {
            Util.deleteRecursive(tempDir);
        }
        branchInfos.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return branchInfos;
    }

    /**
     * Fetches branch tips into a repository under
     * {@code $JENKINS_HOME/caches/active-git-branches/} that is reused across
     * refreshes, then reads commit times from it.
     */
    private static List<BranchInfo> fetchBranchesWithCacheRepository(CacheKey key) throws IOException, InterruptedException {
        File repoDir = new File(Jenkins.get().getRootDir(),
                "caches/active-git-branches/" + Util.getDigestOf(key.repositoryUrl()));
        synchronized (REPO_LOCKS.computeIfAbsent(repoDir.getAbsolutePath(), k -> new Object())) {
            GitClient git = createGitClient(repoDir, key);
            // Non-bare because git-client always opens <dir>/.git; nothing is ever checked out
            if (!new File(repoDir, ".git/HEAD").isFile()) {
                Util.deleteRecursive(repoDir);
                Files.createDirectories(repoDir.toPath());
                git.init_().workspace(repoDir.getAbsolutePath()).execute();
            }
            try {
                git.fetch_()
                        .from(new URIish(key.repositoryUrl()),
                                Collections.singletonList(new RefSpec("+refs/heads/*:" + REMOTE_BRANCH_PREFIX + "*")))
                        .prune(true)
                        .shallow(true)
                        .depth(1)
                        .execute();
            } catch (java.net.URISyntaxException e) {
                throw new IOException("Invalid repository URL: " + key.repositoryUrl(), e);
            }
            List<BranchInfo> branchInfos = git.withRepository(new ReadBranchesCallback());
            branchInfos.sort((a, b) -> Long.compare(b.getCommitTime(), a.getCommitTime()));
            return branchInfos;
        }
    }

    private static GitClient createGitClient(File dir, CacheKey key) throws IOException, InterruptedException {
        TaskListener listener = new LogTaskListener(LOGGER, Level.FINE);
        GitClient git = Git.with(listener, new EnvVars()).in(dir).using("jgit").getClient();
        StandardCredentials credentials = lookupCredentials(key.repositoryUrl(), key.credentialsId());
        if (credentials != null) {
            git.addCredentials(key.repositoryUrl(), credentials);
        }
        return git;
    }

    /** Reads {@code refs/remotes/origin/*} with the commit time of each tip. */
    private static final class ReadBranchesCallback implements RepositoryCallback<List<BranchInfo>> {
        private static final long serialVersionUID = 1L;

        @Override
        public List<BranchInfo> invoke(Repository repo, hudson.remoting.VirtualChannel channel) throws IOException {
            List<BranchInfo> branchInfos = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repo)) {
                for (Ref ref : repo.getRefDatabase().getRefsByPrefix(REMOTE_BRANCH_PREFIX)) {
                    String branchName = ref.getName().substring(REMOTE_BRANCH_PREFIX.length());
                    if (branchName.equals("HEAD")) continue;
                    long commitTime = 0L;
                    try {
                        commitTime = walk.parseCommit(ref.getObjectId()).getCommitTime() * 1000L;
                    } catch (IOException e) {
                        // If we can't parse commit, treat as old
                    }
                    branchInfos.add(new BranchInfo(branchName, commitTime));
                }
            }
            return branchInfos;
        }
    }

    /**
     * Applies branchFilter, alwaysIncludeBranches, excludeBranches and
     * maxBranchCount to an unfiltered, already sorted branch list.
     */
    List<BranchInfo> filterAndLimit(List<BranchInfo> allBranches) {
        Pattern filter = compileOrNull(branchFilter, "branch filter");
        Pattern alwaysInclude = compileOrNull(alwaysIncludeBranches, "always include branches");
        Pattern exclude = compileOrNull(excludeBranches, "exclude branches");

        List<BranchInfo> filtered = new ArrayList<>();
        List<BranchInfo> mandatory = new ArrayList<>();
        List<BranchInfo> others = new ArrayList<>();
        for (BranchInfo info : allBranches) {
            String name = info.getName();
            boolean isAlwaysIncluded = alwaysInclude != null && alwaysInclude.matcher(name).matches();
            if (!isAlwaysIncluded && filter != null && !filter.matcher(name).matches()) {
                continue;
            }
            boolean isDisabled = exclude != null && exclude.matcher(name).matches();
            BranchInfo branch = new BranchInfo(name, info.getCommitTime(), isDisabled);
            filtered.add(branch);
            if (isAlwaysIncluded) {
                mandatory.add(branch);
            } else {
                others.add(branch);
            }
        }

        if (filtered.size() <= maxBranchCount) {
            return filtered;
        }

        // Always-included branches first, then fill remaining slots with the others
        List<BranchInfo> result = new ArrayList<>(mandatory);
        int slotsLeft = maxBranchCount - mandatory.size();
        for (int i = 0; i < slotsLeft && i < others.size(); i++) {
            result.add(others.get(i));
        }
        return result;
    }

    private static Pattern compileOrNull(String regex, String what) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        try {
            return Pattern.compile(regex.trim());
        } catch (PatternSyntaxException e) {
            LOGGER.fine("Ignoring invalid " + what + " regex: " + regex);
            return null;
        }
    }

    public boolean matchesExcludeBranches(String branchName) {
        if (branchName == null || branchName.isEmpty()) {
            return false;
        }
        Pattern pattern = compileOrNull(excludeBranches, "exclude branches");
        return pattern != null && pattern.matcher(branchName).matches();
    }

    private static StandardCredentials lookupCredentials(String repositoryUrl, String credentialsId) {
        if (credentialsId == null || credentialsId.isEmpty()) {
            return null;
        }

        return CredentialsMatchers.firstOrNull(
                com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        Jenkins.get(),
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(repositoryUrl).build()
                ),
                CredentialsMatchers.withId(credentialsId)
        );
    }

    /**
     * Branch information holder. Kept as a JavaBean (not a record) because
     * index.jelly reads {@code branch.name} / {@code branch.disabled}.
     */
    public static class BranchInfo {
        private final String name;
        private final long commitTime;
        private final boolean disabled;

        public BranchInfo(String name, long commitTime) {
            this(name, commitTime, false);
        }

        public BranchInfo(String name, long commitTime, boolean disabled) {
            this.name = name;
            this.commitTime = commitTime;
            this.disabled = disabled;
        }

        public String getName() {
            return name;
        }

        public long getCommitTime() {
            return commitTime;
        }

        public boolean isDisabled() {
            return disabled;
        }
    }

    /**
     * Identifies a cached, unfiltered branch list. Filters are applied per
     * parameter on read, so every parameter pointing at the same repository
     * with the same credentials and fetch mode shares one fetch.
     */
    record CacheKey(String repositoryUrl, String credentialsId, boolean useQuickFetch) {}

    /**
     * Immutable cached snapshot. {@code lastRefreshFailed} signals that the
     * branch list shown to the user may be out of date because the most recent
     * background refresh hit an error; the data itself is kept around so the UI
     * keeps working.
     */
    record CacheEntry(List<BranchInfo> branches, long fetchedAt, boolean lastRefreshFailed) {
        CacheEntry {
            branches = List.copyOf(branches);
        }

        CacheEntry withRefreshFailed() {
            return new CacheEntry(branches, fetchedAt, true);
        }
    }

    private static final class BranchLoader implements CacheLoader<CacheKey, CacheEntry> {
        @Override
        public CacheEntry load(@NonNull CacheKey key) throws Exception {
            return new CacheEntry(fetchAllBranches(key), System.currentTimeMillis(), false);
        }

        @Override
        public CacheEntry reload(@NonNull CacheKey key, @NonNull CacheEntry oldValue) {
            try {
                return load(key);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Background branch refresh failed for " + key.repositoryUrl()
                        + "; keeping previous cached list", e);
                // Always a new instance so Caffeine restarts the refresh interval
                return oldValue.withRefreshFailed();
            }
        }
    }

    @Symbol("activeGitBranches")
    @Extension
    public static class DescriptorImpl extends ParameterDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Active Git Branches Parameter";
        }

        /**
         * Validates the repository URL.
         */
        // Validates caller-supplied string input only; returns no private data and has no side effects.
        @SuppressWarnings({"lgtm[jenkins/no-permission-check]", "lgtm[jenkins/csrf]"})
        public FormValidation doCheckRepositoryUrl(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.error("Repository URL is required");
            }
            if (!value.startsWith("http://") && !value.startsWith("https://") && 
                !value.startsWith("git@") && !value.startsWith("ssh://")) {
                return FormValidation.warning("URL should start with http://, https://, git@ or ssh://");
            }
            return FormValidation.ok();
        }

        /**
         * Validates the max branch count.
         */
        // Validates caller-supplied numeric input only; returns no private data and has no side effects.
        @SuppressWarnings({"lgtm[jenkins/no-permission-check]", "lgtm[jenkins/csrf]"})
        public FormValidation doCheckMaxBranchCount(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.error("Max branch count is required");
            }
            try {
                int count = Integer.parseInt(value);
                if (count <= 0) {
                    return FormValidation.error("Max branch count must be greater than 0");
                }
                if (count > 100) {
                    return FormValidation.warning("Large branch counts may cause performance issues");
                }
            } catch (NumberFormatException e) {
                return FormValidation.error("Please enter a valid number");
            }
            return FormValidation.ok();
        }

        /**
         * Validates the branch filter regex.
         */
        // Validates caller-supplied regex input only; returns no private data and has no side effects.
        @SuppressWarnings({"lgtm[jenkins/no-permission-check]", "lgtm[jenkins/csrf]"})
        public FormValidation doCheckBranchFilter(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Invalid regex pattern: " + e.getMessage());
            }
            return FormValidation.ok();
        }
        
        /**
         * Validates the always include branches regex.
         */
        // Validates caller-supplied regex input only; returns no private data and has no side effects.
        @SuppressWarnings({"lgtm[jenkins/no-permission-check]", "lgtm[jenkins/csrf]"})
        public FormValidation doCheckAlwaysIncludeBranches(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Invalid regex pattern: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        /**
         * Validates the exclude branches regex.
         */
        // Validates caller-supplied regex input only; returns no private data and has no side effects.
        @SuppressWarnings({"lgtm[jenkins/no-permission-check]", "lgtm[jenkins/csrf]"})
        public FormValidation doCheckExcludeBranches(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException e) {
                return FormValidation.error("Invalid regex pattern: " + e.getMessage());
            }
            return FormValidation.ok();
        }

        /**
         * Fills the credentials dropdown.
         */
        // lgtm[jenkins/csrf] Credentials dropdown population is read-only; permission checks below prevent credentials enumeration.
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item,
                                                      @QueryParameter String credentialsId,
                                                      @QueryParameter String repositoryUrl) {
            StandardListBoxModel result = new StandardListBoxModel();
            
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else {
                if (!item.hasPermission(Item.EXTENDED_READ) && 
                    !item.hasPermission(com.cloudbees.plugins.credentials.CredentialsProvider.USE_ITEM)) {
                    return result.includeCurrentValue(credentialsId);
                }
            }

            result.includeEmptyValue();
            result.includeMatchingAs(
                    item instanceof hudson.model.Queue.Task 
                            ? ((hudson.model.Queue.Task) item).getDefaultAuthentication() 
                            : ACL.SYSTEM,
                    item,
                    StandardCredentials.class,
                    URIRequirementBuilder.fromUri(repositoryUrl).build(),
                    CredentialsMatchers.anyOf(
                            CredentialsMatchers.instanceOf(StandardUsernameCredentials.class)
                    )
            );

            return result.includeCurrentValue(credentialsId);
        }

        /**
         * Test connection to the repository.
         */
        @RequirePOST
        public FormValidation doTestConnection(@AncestorInPath Item item,
                                               @QueryParameter String repositoryUrl,
                                               @QueryParameter String credentialsId,
                                               @QueryParameter int maxBranchCount,
                                               @QueryParameter String branchFilter,
                                               @QueryParameter String alwaysIncludeBranches,
                                               @QueryParameter String excludeBranches,
                                               @QueryParameter boolean useQuickFetch) {
            if (!hasConfigurePermission(item)) {
                return FormValidation.ok();
            }
            if (repositoryUrl == null || repositoryUrl.isBlank()) {
                return FormValidation.error("Repository URL is required");
            }

            ActiveGitBranchesParameterDefinition tempDef = 
                    new ActiveGitBranchesParameterDefinition("temp", repositoryUrl, 
                            maxBranchCount > 0 ? maxBranchCount : 10, null);
            tempDef.setCredentialsId(credentialsId);
            tempDef.setBranchFilter(branchFilter);
            tempDef.setAlwaysIncludeBranches(alwaysIncludeBranches);
            tempDef.setExcludeBranches(excludeBranches);
            tempDef.setUseQuickFetch(useQuickFetch);

            try {
                // Bypass the cache so the result reflects the current form values and server state
                List<BranchInfo> branches = tempDef.filterAndLimit(fetchAllBranches(tempDef.buildCacheKey()));
                if (branches.isEmpty()) {
                    return FormValidation.warning("Connection successful, but no branches found matching the filter");
                }
                return FormValidation.ok("Success! Found " + branches.size() + " branches. " +
                        "Latest: " + branches.get(0).getName());
            } catch (Exception e) {
                return FormValidation.error("Failed to connect: " + e.getMessage());
            }
        }

        private static boolean hasConfigurePermission(Item item) {
            if (item != null) {
                return item.hasPermission(Item.CONFIGURE);
            }
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            return jenkins != null && jenkins.hasPermission(Jenkins.ADMINISTER);
        }
    }
}
