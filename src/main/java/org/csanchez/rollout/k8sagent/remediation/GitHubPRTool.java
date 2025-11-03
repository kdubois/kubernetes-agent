package org.csanchez.rollout.k8sagent.remediation;

import dev.langchain4j.agent.tool.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import org.kohsuke.github.*;

import io.quarkus.logging.Log;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Map;

/**
 * Tool that creates GitHub PRs with fixes.
 * Git operations are deterministic, only the fix content comes from AI.
 */
@ApplicationScoped
public class GitHubPRTool {
    
    private final GitOperations gitOps;
    private final GitHub github;
    
    public GitHubPRTool() throws Exception {
        this.gitOps = new GitOperations();
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isEmpty()) {
            Log.warn("GITHUB_TOKEN environment variable not set");
            this.github = null;
        } else {
            this.github = new GitHubBuilder().withOAuthToken(token).build();
            Log.info("GitHub PR tool initialized");
        }
    }
    
    /**
     * Create a GitHub pull request with code fixes
     * 
     * @param repoUrl URL of the GitHub repository
     * @param fileChanges Map of file paths to their new content
     * @param fixDescription Description of the fix
     * @param rootCause Root cause of the issue
     * @param namespace Kubernetes namespace
     * @param podName Kubernetes pod name
     * @param testingRecommendations Testing recommendations
     * @return Result of the PR creation
     */
    @Tool("Create a GitHub pull request with code fixes")
    public Map<String, Object> createGitHubPR(
            String repoUrl,
            Map<String, String> fileChanges,
            String fixDescription,
            String rootCause,
            String namespace,
            String podName,
            String testingRecommendations
    ) {
        Log.info("=== Executing Tool: createGitHubPR ===");
        
        if (github == null) {
            return Map.of("success", false, "error", "GITHUB_TOKEN environment variable is required");
        }
        
        if (repoUrl == null || fileChanges == null || fixDescription == null) {
            return Map.of("success", false, "error", "Missing required parameters: repoUrl, fileChanges, fixDescription");
        }
        Log.info(MessageFormat.format("Creating PR for repository: {0}", repoUrl));
        
        
        // Deterministic git workflow (HOW to fix):
        String branchName = MessageFormat.format("fix/k8s-issue-{0}", System.currentTimeMillis());
        String token = System.getenv("GITHUB_TOKEN");
        Path repoPath = null;
        
        try {
            // 1. Clone (library)
            repoPath = gitOps.cloneRepository(repoUrl, token);
            
            // 2. Create branch (library)
            gitOps.createBranch(repoPath, branchName);
            
            // 3. Apply AI-suggested changes (library file I/O)
            gitOps.applyChanges(repoPath, fileChanges);
            
            // 4. Commit and push (library)
            String commitMsg = MessageFormat.format("fix: {0}", fixDescription);
            gitOps.commitAndPush(repoPath, commitMsg, token);
            
            // 5. Create PR via GitHub API (library)
            String repoName = extractRepoName(repoUrl);
            GHRepository repo = github.getRepository(repoName);
            
            String baseBranch = repo.getDefaultBranch();
            String prTitle = MessageFormat.format("Fix: {0}", fixDescription);
            String prBody = generatePRBody(rootCause, fixDescription, testingRecommendations, namespace, podName, fileChanges);
            
            GHPullRequest pr = repo.createPullRequest(
                prTitle,
                branchName,
                baseBranch,
                prBody
            );
            Log.info(MessageFormat.format("Successfully created PR: {0}", pr.getHtmlUrl()));
            
            
            return Map.of(
                "success", true,
                "prUrl", pr.getHtmlUrl().toString(),
                "prNumber", pr.getNumber(),
                "branch", branchName
            );
            
        } catch (Exception e) {
            Log.error("Failed to create PR", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        } finally {
            // Cleanup temporary directory
            if (repoPath != null) {
                gitOps.cleanup(repoPath);
            }
        }
    }
    
    /**
     * Extract repository name from URL (e.g., "owner/repo")
     */
    private String extractRepoName(String repoUrl) {
        // Handle formats: https://github.com/owner/repo or https://github.com/owner/repo.git
        String cleaned = repoUrl.replace("https://github.com/", "")
            .replace(".git", "");
        return cleaned;
    }
    
    /**
     * Generate PR body with analysis results
     */
    private String generatePRBody(
            String rootCause,
            String fixDescription,
            String testingRecommendations,
            String namespace,
            String podName,
            Map<String, String> fileChanges
    ) {
        String changesSummary = fileChanges != null ? 
            String.join(", ", fileChanges.keySet()) : "No files changed";
        
        if (testingRecommendations == null || testingRecommendations.isEmpty()) {
            testingRecommendations = "Run existing test suite";
        }
        
        if (rootCause == null || rootCause.isEmpty()) {
            rootCause = "Not available";
        }
        
        if (namespace == null) {
            namespace = "unknown";
        }
        
        if (podName == null) {
            podName = "unknown";
        }
        
        return String.format("""
            ## Root Cause Analysis
            %s
            
            ## Changes Made
            Modified files: %s
            
            %s
            
            ## Testing Recommendations
            %s
            
            ## Related Kubernetes Resources
            - **Namespace**: `%s`
            - **Pod**: `%s`
            
            ---
            *This PR was automatically generated by Kubernetes AI Agent*
            *Review carefully before merging*
            """,
            rootCause,
            changesSummary,
            fixDescription,
            testingRecommendations,
            namespace,
            podName
        );
    }
}


