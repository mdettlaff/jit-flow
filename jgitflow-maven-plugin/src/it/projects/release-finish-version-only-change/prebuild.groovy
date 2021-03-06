import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    FinishScriptHelper.Gits gits = helper.createAndCloneRepo("1.0","1.2-SNAPSHOT","1.1","release/")

    Git localGit = gits.local;
    localGit.checkout().setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).setStartPoint("origin/release/1.1").setName("release/1.1").call()
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}