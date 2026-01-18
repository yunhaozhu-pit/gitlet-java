package gitlet.command;

import gitlet.cli.Command;
import gitlet.core.RepoService;

public class FetchCommand implements Command {
    private final RepoService service;

    public FetchCommand(RepoService service) {
        this.service = service;
    }

    @Override
    public int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Incorrect operands.");
            return 1;
        }
        service.fetch(args[0], args[1]);
        return 0;
    }
}
