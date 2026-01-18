
package gitlet;

import gitlet.command.*;
import gitlet.cli.*;
import gitlet.core.*;
import gitlet.errors.*;
import gitlet.storage.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static gitlet.Utils.error;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        try {
            File cwd = new File(System.getProperty("user.dir"));
            ObjectRS objects = new FileObjectRS(cwd);
            RefRS refs = new FileRefRS(cwd);
            Worktree worktree = new FileWorktree(cwd);
            RepoService service = new RepoService(objects, refs, worktree);

            Map<String, Command> registry = new HashMap<>();
            registry.put("init", new InitCommand(service));
            registry.put("add", new AddCommand(service));
            registry.put("rm", new RemoveCommand(service));
            registry.put("log", new LogCommand(service));
            registry.put("global-log", new GlobalLogCommand(service));
            registry.put("find", new FindCommand(service));
            registry.put("status", new ShowStatusCommand(service));
            registry.put("commit", new CommitCommand(service));
            registry.put("branch", new BranchCommand(service));
            registry.put("rm-branch", new RemoveBranchCommand(service));
            registry.put("checkout", new CheckoutCommand(service));
            registry.put("merge", new MergeCommand(service));
            registry.put("reset", new ResetCommand(service));
            registry.put("add-remote", new AddRemoteCommand(service));
            registry.put("rm-remote", new RemoveRemoteCommand(service));
            registry.put("push", new PushCommand(service));
            registry.put("fetch", new FetchCommand(service));
            registry.put("pull", new PullCommand(service));

            Command cmd = registry.get(args[0]);
            if (cmd == null) {
                System.out.println("No command with that name exists.");
                System.exit(0);
            }

            String[] sub = java.util.Arrays.copyOfRange(args, 1, args.length);
            if (!args[0].equals("init") && !new File(".gitlet").exists()) {
                throw error("Not in an initialized Gitlet directory.");
            }
            int code = cmd.run(sub);
            System.exit(code);
        } catch (GitletException ge) {
            System.out.println(ge.getMessage());
            System.exit(0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
