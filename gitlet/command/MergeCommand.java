package gitlet.command;

import gitlet.cli.Command;
import gitlet.core.RepoService;


public class MergeCommand implements Command {
    private final RepoService service;

    public MergeCommand(RepoService service) {
        this.service = service;
    }

    @Override
    public int run(String[] args) {
        if (args.length != 1) {
            System.err.println("Incorrect operands.");
            return 1;
        }

        service.merge(args[0]);
        return 0;
    }
}
