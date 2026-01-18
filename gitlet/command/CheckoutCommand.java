package gitlet.command;

import gitlet.Utils;
import gitlet.cli.Command;
import gitlet.core.RepoService;


public class CheckoutCommand implements Command {
    enum CheckoutMode {FILE_FROM_HEAD, FILE_FROM_COMMIT, BRANCH}

    private final RepoService service;

    public CheckoutCommand(RepoService service) {
        this.service = service;
    }

    @Override
    public int run(String[] args) {
        CheckoutMode mode = parseCheckoutArgs(args);

        switch (mode) {
            case FILE_FROM_HEAD:
                service.checkHeadFile(args[1]);
                break;

            case FILE_FROM_COMMIT:
                service.checkCommitFile(args[0], args[2]);
                break;

            case BRANCH:
                service.switchBranch(args[0]);
                break;

            default:
                break;
        }
        return 0;
    }

    private static CheckoutMode parseCheckoutArgs(String[] args) {
        if (args.length == 2 && "--".equals(args[0])) {
            return CheckoutMode.FILE_FROM_HEAD;      // checkout -- <file>
        }
        if (args.length == 3 && "--".equals(args[1])) {
            return CheckoutMode.FILE_FROM_COMMIT;    // checkout <commitId> -- <file>
        }
        if (args.length == 1) {
            return CheckoutMode.BRANCH;              // checkout <branch>
        }
        throw Utils.error("Incorrect operands.");
    }
}
