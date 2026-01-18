package gitlet.command;

import gitlet.cli.Command;
import gitlet.core.RepoService;


public class CommitCommand implements Command {
    private final RepoService service;

    public CommitCommand(RepoService service) {
        this.service = service;
    }

    @Override
    public int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Incorrect operands.");
            return 1;
        }

        service.commit(args[0]);
        return 0;
    }
}
