package ut.com.atlassian.jgitflow.core;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.util.GitHelper;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;
import ut.com.atlassian.jgitflow.core.testutils.RepoUtil;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @since version
 */
public class HotfixFinishTest extends BaseGitFlowTest
{
    @Test
    public void finishHotfix() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        assertEquals(flow.getHotfixBranchPrefix() + "1.1", git.getRepository().getBranch());

        flow.hotfixFinish("1.1").call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //release branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getHotfixBranchPrefix() + "1.1");
        assertNull(ref2check);

    }

    @Test
    public void finishHotfixMultipleTimesWithCommits() throws Exception
    {
        String hfOneLabel = "1.0.1";
        String hfTwoLabel = "1.0.2";

        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart(hfOneLabel).call();

        assertEquals(flow.getHotfixBranchPrefix() + hfOneLabel, git.getRepository().getBranch());

        File versionFile = new File(git.getRepository().getWorkTree(), "version.txt");
        FileUtils.writeStringToFile(versionFile, hfOneLabel);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("commiting version 1").call();

        flow.hotfixFinish(hfOneLabel).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());
        FileUtils.writeStringToFile(versionFile, "master");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("commiting develop").call();

        flow.hotfixStart(hfTwoLabel).call();

        assertEquals(flow.getHotfixBranchPrefix() + hfTwoLabel, git.getRepository().getBranch());

        FileUtils.writeStringToFile(versionFile, hfTwoLabel);
        git.add().addFilepattern(".").call();
        git.commit().setMessage("commiting version 2").call();

        flow.hotfixFinish(hfTwoLabel).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

    }

    @Test(expected = DirtyWorkingTreeException.class)
    public void finishHotfixWithUnStagedFile() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //create a new file
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");

        //try to finish
        flow.hotfixFinish("1.1").call();
    }

    @Test(expected = DirtyWorkingTreeException.class)
    public void finishHotfixUnCommittedFile() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //create a new file and add it to the index
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        git.add().addFilepattern(junkFile.getName()).call();

        //try to finish
        flow.hotfixFinish("1.1").call();
    }

    @Test
    public void finishHotfixWithNewCommit() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //create a new commit
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        git.add().addFilepattern(junkFile.getName()).call();
        RevCommit commit = git.commit().setMessage("committing junk file").call();

        //make sure develop doesn't report our commit yet
        assertFalse(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));

        //try to finish
        flow.hotfixFinish("1.1").call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //release branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getHotfixBranchPrefix() + "1.1");
        assertNull(ref2check);

        //the develop branch should have our commit
        assertTrue(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));

        //since fast-forward is suppressed the latest commit on develop should be a merge commit with 2 parents
        assertEquals(2, GitHelper.getLatestCommit(git, flow.getDevelopBranchName()).getParentCount());

        //the master branch should have our commit
        assertTrue(GitHelper.isMergedInto(git, commit, getTaggedCommit(git, "1.1")));

        //since fast-forward is suppressed the latest commit on master should be a merge commit with 2 parents
        assertEquals(2, GitHelper.getLatestCommit(git, getTaggedCommit(git, "1.1")).getParentCount());
    }

    @Test
    public void finishHotfixWithNewCommitAndReleaseBranch() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.releaseStart("1.1").call();

        String releaseName = "release/1.1";

        flow.git().checkout().setName("master").call();

        flow.hotfixStart("1.1.1").call();

        //create a new commit
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        git.add().addFilepattern(junkFile.getName()).call();
        RevCommit commit = git.commit().setMessage("committing junk file").call();

        //make sure develop doesn't report our commit yet
        assertFalse(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));

        //make sure release doesn't report our commit yet
        assertFalse(GitHelper.isMergedInto(git, commit, releaseName));

        //try to finish
        flow.hotfixFinish("1.1.1").setKeepBranch(false).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //hotfix branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getHotfixBranchPrefix() + "1.1.1");
        assertNull(ref2check);

        //the develop branch should have our commit
        assertTrue(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));

        //since fast-forward is suppressed the latest commit on develop should be a merge commit with 2 parents
        assertEquals(2, GitHelper.getLatestCommit(git, flow.getDevelopBranchName()).getParentCount());

        //the master branch should have our commit
        assertTrue(GitHelper.isMergedInto(git, commit, getTaggedCommit(git, "1.1.1")));

        //since fast-forward is suppressed the latest commit on master should be a merge commit with 2 parents
        assertEquals(2, GitHelper.getLatestCommit(git, getTaggedCommit(git, "1.1.1")).getParentCount());

        //the release branch should have our commit
        assertTrue(GitHelper.isMergedInto(git, commit, releaseName));

        //since fast-forward is suppressed the latest commit on the release branch should be a merge commit with 2 parents
        assertEquals(2, GitHelper.getLatestCommit(git, releaseName).getParentCount());
    }

    @Test
    public void finishHotfixKeepBranch() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //just in case
        assertEquals(flow.getHotfixBranchPrefix() + "1.1", git.getRepository().getBranch());

        flow.hotfixFinish("1.1").setKeepBranch(true).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //release branch should still exist
        Ref ref2check = git.getRepository().getRef(flow.getHotfixBranchPrefix() + "1.1");
        assertNotNull(ref2check);
    }

    @Test
    public void finishHotfixWithMultipleCommits() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //create a new commit
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        git.add().addFilepattern(junkFile.getName()).call();
        RevCommit commit = git.commit().setMessage("committing junk file").call();

        //create second commit
        File junkFile2 = new File(git.getRepository().getWorkTree(), "junk2.txt");
        FileUtils.writeStringToFile(junkFile2, "I am junk, and so are you");
        git.add().addFilepattern(junkFile2.getName()).call();
        RevCommit commit2 = git.commit().setMessage("updating junk file").call();

        //make sure develop doesn't have our commits yet
        assertFalse(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));
        assertFalse(GitHelper.isMergedInto(git, commit2, flow.getDevelopBranchName()));

        //try to finish
        flow.hotfixFinish("1.1").call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //release branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getHotfixBranchPrefix() + "1.1");
        assertNull(ref2check);

        //the develop branch should have both of our commits now
        assertTrue(GitHelper.isMergedInto(git, commit, flow.getDevelopBranchName()));
        assertTrue(GitHelper.isMergedInto(git, commit2, flow.getDevelopBranchName()));

        //the master branch should have both of our commits now
        assertTrue(GitHelper.isMergedInto(git, commit, getTaggedCommit(git, "1.1")));
        assertTrue(GitHelper.isMergedInto(git, commit2, getTaggedCommit(git, "1.1")));
    }

    @Test(expected = BranchOutOfDateException.class)
    public void finishHotfixDevelopBehindRemoteWithFetch() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndTag(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //do a commit to the remote develop branch
        remoteGit.checkout().setName(flow.getDevelopBranchName());
        File junkFile = new File(remoteGit.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        remoteGit.add().addFilepattern(junkFile.getName()).call();
        remoteGit.commit().setMessage("adding junk file").call();

        flow.hotfixFinish("1.1").setFetch(true).call();

    }

    @Test(expected = BranchOutOfDateException.class)
    public void finishHotfixMasterBehindRemoteWithFetch() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndTag(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        //do a commit to the remote develop branch
        remoteGit.checkout().setName(flow.getDevelopBranchName());
        File junkFile = new File(remoteGit.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        remoteGit.add().addFilepattern(junkFile.getName()).call();
        remoteGit.commit().setMessage("adding junk file").call();

        flow.hotfixFinish("1.1").setFetch(true).call();

    }

    @Test
    public void finishHotfixMasterIsTagged() throws Exception
    {
        Git git = RepoUtil.createRepositoryWithMasterAndTag(newDir());
        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        flow.hotfixStart("1.1").call();

        // Make sure we move away from master on a hotfix branch
        // This is important to validate JGITFFLOW-14

        //create a new commit
        File junkFile = new File(git.getRepository().getWorkTree(), "junk.txt");
        FileUtils.writeStringToFile(junkFile, "I am junk");
        git.add().addFilepattern(junkFile.getName()).call();
        git.commit().setMessage("committing junk file").call();

        flow.hotfixFinish("1.1").setNoTag(false).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        // There should be a tag reference
        Ref hotfixTagRef = git.getRepository().getTags().get(flow.getVersionTagPrefix() + "1.1");

        assertNotNull(hotfixTagRef);

        RevTag hotfixTag = new RevWalk(git.getRepository()).parseTag(hotfixTagRef.getObjectId());

        assertNotNull(hotfixTag);

        // Check that latest tag has moved
        assertFalse(getTaggedCommit(git, "1.1").equals(getTaggedCommit(git, "1.0")));
    }

    //TODO: add tests for push and tag flags

    private String getTaggedCommit(Git git, String tagName) throws JGitFlowIOException, IOException {
        return GitHelper.getTaggedCommit(git, git.getRepository().getRef(tagName));
    }
}