package gitlet.storage;

import gitlet.core.Commit;
import gitlet.core.Stage;

import java.util.List;
import java.util.Set;

public interface ObjectRS {

    void writeCommit(Commit commit);

    void writeStage(Stage stage);

    Stage readStage();

    Commit readCommit(String name);

    Commit getIdCommit(String prefix);

    List<String> matchedCommit(String commitId);

    void initialize();

    boolean existCommit(String commitId);

    void iterateNodes(Commit C, Set<String> anc, Set<String> visited);
}
