package gitlet.storage;

import gitlet.Utils;

import java.io.File;

import static gitlet.Utils.*;

public class FileWorktree implements Worktree {
    private final File CWD;

    public FileWorktree(File cwd) {
        this.CWD = cwd;
    }

    @Override
    public boolean exists(String name) {
        File wf = join(CWD, name);
        return wf.exists();
    }

    @Override
    public byte[] readFileCWD(String fileName) {
        File f = Utils.join(CWD, fileName);
        return Utils.readContents(f);
    }

    @Override
    public boolean exist(String fileName) {
        File f = Utils.join(CWD, fileName);
        return f.exists();
    }

    @Override
    public void remove(String fileName) {
        File f = Utils.join(CWD, fileName);
        if (f.exists()) {
            Utils.restrictedDelete(f);
        }
    }

    @Override
    public void write(String name, byte[] content) {
        Utils.writeContents(join(CWD, name), content);
    }
}
