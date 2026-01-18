package gitlet.storage;

import gitlet.Utils;
import gitlet.core.Commit;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static gitlet.Utils.*;


public class FileRefRS implements RefRS {
    private final File CWD;
    private final File GITLET_DIR;
    private final File REFS_FOLDER;
    private final File HEAD_FILE;
    private final File HEADS_FILE;
    private final File BLOBS_FOLDER;
    private final File REMOTE_FILE;

    public FileRefRS(File cwd) {
        this.CWD = cwd;
        this.GITLET_DIR = Utils.join(CWD, ".gitlet");
        this.REFS_FOLDER = Utils.join(GITLET_DIR, "refs");
        this.HEAD_FILE = Utils.join(GITLET_DIR, "HEAD");
        this.HEADS_FILE = Utils.join(REFS_FOLDER, "heads");
        this.BLOBS_FOLDER = Utils.join(GITLET_DIR, "blobs");
        this.REMOTE_FILE = Utils.join(REFS_FOLDER, "remotes");
    }

    @Override
    public boolean existBranch(String branchName) {
        File branchHead = join(HEADS_FILE, branchName);
        return branchHead.exists();
    }

    @Override
    public boolean existsRepo() {
        return GITLET_DIR.exists();
    }

    @Override
    public void initialize() {
        if (!GITLET_DIR.mkdirs()) {
            throw error("Cannot create " + GITLET_DIR);
        }
        if (!BLOBS_FOLDER.mkdirs()) {
            throw error("Cannot create " + BLOBS_FOLDER);
        }
        if (!REFS_FOLDER.mkdirs()) {
            throw error("Cannot create " + REFS_FOLDER);
        }
        if (!HEADS_FILE.mkdirs()) {
            throw error("Cannot create " + REFS_FOLDER);
        }
    }


    @Override
    public void movePointer(String commitID) {
        String branch = Utils.readContentsAsString(HEAD_FILE).trim();
        File f = Utils.join(HEADS_FILE, branch);
        Utils.writeContents(f, commitID);
    }

    @Override
    public void writeBlobFile(String blobId, byte[] content) {
        File f = join(BLOBS_FOLDER, blobId);
        if (!f.exists()) {
            writeContents(f, content);
        }
    }

    @Override
    public void writeCWD(String name, byte[] content) {
        writeContents(join(CWD, name), content);
    }

    @Override
    public void saveHeadName(String branchName) {
        writeContents(HEAD_FILE, branchName);
    }

    @Override
    public void saveBranch(String branchName, Commit commit) {
        // Needs a exist judgement.
        File branchHead = join(HEADS_FILE, branchName);
        writeContents(branchHead, commit.getID());
    }

    @Override
    public byte[] readBlobFile(String blobId) {
        // Need a situation judge.
        File blobFile = join(BLOBS_FOLDER, blobId);
        return readContents(blobFile);
    }

    @Override
    public List<String> readFileNameInFolder(String folder) {
        if ("COMMITS".equals(folder)) {
            return Utils.plainFilenamesIn(Utils.join(GITLET_DIR, "commits"));
        }
        if ("HEADS".equals(folder)) {
            return Utils.plainFilenamesIn(HEADS_FILE);
        }
        if ("CWD".equals(folder)) {
            return Utils.plainFilenamesIn(CWD);
        }
        return null;
    }

    @Override
    public boolean existBlobFile(String blobId) {
        File f = Utils.join(BLOBS_FOLDER, blobId);
        return f.exists();
    }

    @Override
    public String readHEAD() {
        return readContentsAsString(HEAD_FILE).trim();
    }

    @Override
    public Commit getHeadCommit() {
        String branch = Utils.readContentsAsString(HEAD_FILE).trim();
        String id = Utils.readContentsAsString(join(HEADS_FILE, branch)).trim();
        File commitFile = Utils.join(Utils.join(GITLET_DIR, "commits"), id);
        return Utils.readObject(commitFile, Commit.class);
    }

    @Override
    public boolean deleteRootBranch(String branchName) {
        File file = join(HEADS_FILE, branchName);
        File root = findGitletRoot(file);
        if (root == null || !new File(root, ".gitlet").isDirectory()) {
            throw new IllegalArgumentException("not .gitlet working directory");
        }

        if (!file.isDirectory()) {
            return file.delete();
        } else {
            return false;
        }
    }

    @Override
    public String readBranch(String branchName) {
        File branchFile = join(HEADS_FILE, branchName);
        return Utils.readContentsAsString(branchFile).trim();
    }

    @Override
    public boolean existRemote(String remoteName) {
        File file = join(REMOTE_FILE, remoteName);
        return file.exists();
    }

    @Override
    public void writeRemote(String name, String absGitletPath) {
        File file = join(REMOTE_FILE, name);
        writeContents(file, absGitletPath);
    }

    @Override
    public String readRemotePathOrNull(String name) {
        File file = join(REMOTE_FILE, name);
        if (!file.isFile()) {
            return null;
        }
        return readContentsAsString(file).trim();
    }


    @Override
    public String normalizeToAbsoluteGiletPath(String cliPath) {
        String nativePath = cliPath.replace('/', File.separatorChar);

        File f = new File(nativePath);
        if (!f.isAbsolute()) {
            f = new File(CWD, nativePath);
        }

        try {
            f = f.getCanonicalFile();
        } catch (IOException e) {
            f = f.getAbsoluteFile();
        }

        return f.getPath();
    }

    @Override
    public void deleteRemoteFile(String name) {
        File remoteFile = join(REMOTE_FILE, name);
        Utils.rootRestrictDelete(remoteFile);
    }


    private static File findGitletRoot(File file) {
        File current = file;
        while (current != null) {
            if (new File(current, ".gitlet").isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }
}
