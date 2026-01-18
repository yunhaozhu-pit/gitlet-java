package gitlet.command;

import gitlet.cli.Command;
import gitlet.core.RepoService;


public class InitCommand implements Command {
    private final RepoService service;

    public InitCommand(RepoService service) {
        this.service = service;
    }

    @Override
    public int run(String[] args) {
        if (args.length != 0) {
            System.err.println("Incorrect operands.");
            return 1;
        }
        service.init();
        return 0;
    }
}
