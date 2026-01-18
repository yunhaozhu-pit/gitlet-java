package gitlet.storage;

import gitlet.Utils;
import gitlet.core.Commit;
import gitlet.core.Stage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


import static gitlet.Utils.*;


public class FileObjectRS implements ObjectRS {
    private final File GITLET_DIR;
    private final File COMMITS_FOLDER;
    private final File STAGE_FILE;

    public FileObjectRS(File cwd) {
        this.GITLET_DIR = join(cwd, ".gitlet");
        this.COMMITS_FOLDER = join(GITLET_DIR, "commits");
        this.STAGE_FILE = join(GITLET_DIR, "stage");
    }

    @Override
    public void initialize() {
        if (!COMMITS_FOLDER.mkdirs()) {
            throw error("Cannot create " + COMMITS_FOLDER);
        }
    }

    @Override
    public void writeCommit(Commit commit) {
        File commitFile = join(COMMITS_FOLDER, commit.getID());
        Utils.writeObject(commitFile, commit);
    }

    @Override
    public void writeStage(Stage stage) {
        writeObject(STAGE_FILE, stage);
    }

    @Override
    public Stage readStage() {
        return Utils.readObject(STAGE_FILE, Stage.class);
    }

    @Override
    public Commit readCommit(String name) {
        // Needs a existence judge.
        File f = join(COMMITS_FOLDER, name);
        return Utils.readObject(f, Commit.class);
    }

    @Override
    public Commit getIdCommit(String fullId) {
        File theCommit = join(COMMITS_FOLDER, fullId);
        return Utils.readObject(theCommit, Commit.class);
    }


    @Override
    public List<String> matchedCommit(String prefix) {
        List<String> ids = plainFilenamesIn(COMMITS_FOLDER);
        List<String> matches = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return matches;
        }
        for (String id : ids) {
            if (id.startsWith(prefix)) {
                matches.add(id);
            }
        }
        return matches;
    }

    public void iterateNodes(Commit C, Set<String> anc, Set<String> visited) {
        /** Using DFS to find the all ancestor nodes of C */
        if (C == null) {
            return;
        }
        String p1 = C.getParentID();
        if (p1 != null && visited.add(p1)) {
            anc.add(p1);
            Commit first = readObject(join(COMMITS_FOLDER, p1), Commit.class);
            iterateNodes(first, anc, visited);
        }

        String p2 = C.getSecondParentID();
        if (p2 != null && visited.add(p2)) {
            anc.add(p2);
            Commit second = readObject(join(COMMITS_FOLDER, p2), Commit.class);
            iterateNodes(second, anc, visited);
        }
    }


    @Override
    public boolean existCommit(String commitId) {
        File f = join(COMMITS_FOLDER, commitId);
        return f.exists();
    }
}
