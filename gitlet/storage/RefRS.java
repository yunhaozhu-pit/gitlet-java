package gitlet.storage;

import gitlet.core.Commit;

import java.util.List;

public interface RefRS {
    boolean existsRepo();

    void movePointer(String commitID);

    void writeBlobFile(String blobId, byte[] content);

    void writeCWD(String name, byte[] content);

    void saveHeadName(String branchName);

    void saveBranch(String branchName, Commit commit);

    byte[] readBlobFile(String blobId);

    List<String> readFileNameInFolder(String folder);

    boolean existBlobFile(String blobId);

    String readHEAD();

    void initialize();

    boolean existBranch(String brancName);

    Commit getHeadCommit();

    boolean deleteRootBranch(String branchName);

    String readBranch(String branchName);

    boolean existRemote(String remoteName);

    void writeRemote(String name, String absGitletPath);

    String readRemotePathOrNull(String name);

    String normalizeToAbsoluteGiletPath(String cliPath);

    void deleteRemoteFile(String name);
}
