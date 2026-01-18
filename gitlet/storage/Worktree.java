package gitlet.storage;

public interface Worktree {
    boolean exists(String path);

    byte[] readFileCWD(String name);

    boolean exist(String fileName);

    void remove(String fileName);

    void write(String name, byte[] content);
}
