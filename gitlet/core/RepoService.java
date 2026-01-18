package gitlet.core;

import gitlet.Utils;
import gitlet.errors.GitletException;
import gitlet.storage.RefRS;
import gitlet.storage.ObjectRS;
import gitlet.storage.Worktree;
import gitlet.storage.FileRefRS;
import gitlet.storage.FileObjectRS;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Objects;

import static gitlet.Utils.*;


public class RepoService {
    enum MergeAction {REMOVE, WRITE_G, CONFLICT, KEEP}
    private final ObjectRS objectRS;
    private final RefRS refRS;
    private final Worktree worktree;

    public RepoService(ObjectRS objectRS, RefRS refRS, Worktree worktree) {
        this.objectRS = objectRS;
        this.refRS = refRS;
        this.worktree = worktree;
    }

    /**
     * Initializes a new Gitlet repository in the working directory.
     * Creates the internal directory layout, writes an empty initial commit
     * whose message is exactly {@code "initial commit"} and timestamp {@code 0L},
     * creates the {@code master} branch pointing to that commit, and sets HEAD
     * to {@code master}.
     * This operation must run in constant time with respect to any significant measure.
     * @throws GitletException if a Gitlet repository already exists in the current directory.
     */
    public void init() {
        if (refRS.existsRepo()) {
            throw new GitletException("A Gitlet version-control system already exists in the current directory.");
        }
        refRS.initialize();
        objectRS.initialize();
        Commit init = new Commit();
        Stage stage = new Stage();
        refRS.saveHeadName("master");
        refRS.saveBranch("master", init);
        objectRS.writeStage(stage);
        objectRS.writeCommit(init);
    }

    /**
     * Stages the current working-directory contents of {@code fileName} for the next commit.
     * If the working copy is identical to the version tracked by the current commit (HEAD),
     * the file is removed from the addStage (if present) and nothing is staged. If the file
     * had been staged for removal, this call un-stages it from the removal set. When the file's
     * content blob does not yet exist in the object store, a new blob is written and its
     * {@code blobId} recorded in the add-stage.
     *
     * @param fileName name of the file in the working directory.
     * @throws GitletException if the file does not exist in the working directory.
     */

    public void add(String fileName) {
        if (!worktree.exist(fileName)) {
            throw Utils.error("File does not exist.");
        }

        Stage stage = objectRS.readStage();
        stage.removeRS(fileName);

        // Read the head commit and compute the file blob id.
        Commit headCommit = refRS.getHeadCommit();
        byte[] bytes = worktree.readFileCWD(fileName);
        String blobId = Utils.sha1(bytes);

        // Compare with HEAD Commit and remove
        String headBlob = headCommit.getValue(fileName);
        if (blobId.equals(headBlob)) {
            stage.removeAS(fileName);
            objectRS.writeStage(stage);
            return;
        }

        if (!refRS.existBlobFile(blobId)) {
            refRS.writeBlobFile(blobId, bytes);
        }

        stage.addAS(fileName, blobId);
        objectRS.writeStage(stage);
    }


    /**
     * Removes {@code fileName} from the next commit.
     * If the file is staged for addition, this call un-stages it from the add-stage.
     * If the file is tracked by the current commit (HEAD), this call stages it for removal
     * and deletes the working-directory copy if it exists. If the file is neither staged
     * for addition nor tracked by HEAD, prints {@code "No reason to remove the file."}
     * and performs no changes.
     *
     * @param fileName name of the file in the working directory.
     * @throws GitletException if there is no reason to remove the file.
     */
    public void remove(String fileName) {
        Stage stage = objectRS.readStage();
        Commit headCommit = refRS.getHeadCommit();
        boolean did = false;

        if (stage.addStageContainKey(fileName)) {
            stage.removeAS(fileName);
            did = true;
        }

        if (headCommit.blobMapContainKey(fileName)) {
            stage.addRS(fileName);
            worktree.remove(fileName);
            did = true;
        }

        objectRS.writeStage(stage);

        if (!did) {
            throw Utils.error("No reason to remove the file.");
        }
    }

    /**
     * Prints the first-parent commit history starting from HEAD down to the initial commit.
     * For each commit, prints a standard block containing the commit id, date, and message.
     * If a commit has a second parent (i.e., a merge), an extra line
     * {@code "Merge: "} is printed for that commit. The traversal follows only
     * the first parent link and does not expand second-parent histories.
     */
    public void log() {
        Commit cur = refRS.getHeadCommit();
        while (cur != null) {
            cur.printMessage();
            if (cur.getSecondParentID() != null) {
                cur.printMergeMessage();
            }
            String pid = cur.getParentID();
            cur = (pid == null) ? null : objectRS.readCommit(pid);
        }
    }

    /**
     * Prints a log entry for every commit in the repository, in any order.
     * Each entry matches the format used by {@code log}: a block starting with {@code ===},
     * a {@code commit <full-id>} line, an optional {@code Merge: } line for merge
     * commits, a {@code Date: ...} line, and the commit message, followed by a blank line.
     * No particular ordering of commits is required.
     */
    public void globalLog() {
        List<String> ids = refRS.readFileNameInFolder("COMMITS");
        for (String id : ids) {
            Commit cur = objectRS.readCommit(id);
            cur.printMessage();
            if (cur.getSecondParentID() != null) {
                cur.printMergeMessage();                 // Merge: <p1-7> <p2-7>
            }
        }
    }

    /**
     * Prints the full ids of all commits whose commit message equals {@code msg}, one per line.
     * The search scans every commit in the repository and uses exact, case-sensitive
     * equality. If no such commits exist, prints
     * {@code "Found no commit with that message."} and exits without changing state.
     *
     * @param msg the commit message to match exactly.
     */
    public void find(String msg) {
        List<String> ids = refRS.readFileNameInFolder("COMMITS");
        if (ids == null || ids.isEmpty()) {
            throw Utils.error("Found no commit with that message.");
        }

        boolean mark = false;
        for (String id : ids) {
            Commit cur = objectRS.readCommit(id);
            if (msg.equals(cur.getMessage())) {
                mark = true;
                System.out.println(id);
            }
        }
        if (!mark) {
            throw error("Found no commit with that message.");
        }
    }

    /**
     * Prints the repository status in five sections: Branches, Staged Files,
     * Removed Files, Modifications Not Staged For Commit, and Untracked Files.
     * Modifications Not Staged For Commit includes files that are tracked by
     * HEAD but whose working copy differs and has not been re-staged, files that
     * are staged for addition but whose working copy has changed since staging,
     * files staged for addition that have been deleted from the working directory,
     * and files tracked by HEAD that have been deleted from the working directory
     * but are not staged for removal.
     * Untracked Files lists working-directory files that are neither tracked by
     * HEAD nor staged for addition.
     */
    public void showStatus() {
        List<String> headsNames = refRS.readFileNameInFolder("HEADS");
        List<String> fileNames = refRS.readFileNameInFolder("CWD");
        Set<String> seen = new LinkedHashSet<>();
        Stage stage = objectRS.readStage();
        String head = refRS.readHEAD();
        Commit cur = refRS.getHeadCommit();
        Collections.sort(headsNames);

        System.out.println("=== Branches ===");
        System.out.println("*" + head);
        if (!headsNames.isEmpty()) {
            for (String name : headsNames) {
                if (!head.equals(name)) {
                    System.out.println(name);
                }
            }
        }
        System.out.println();

        /** Displays what files have been staged for addition. */
        System.out.println("=== Staged Files ===");
        if (!stage.isEmpty()) {
            for (String name : stage.addStageKeySet()) {
                System.out.println(name);
            }
        }
        System.out.println();

        /** Displays what files have been staged for removal. */
        System.out.println("=== Removed Files ===");
        if (!stage.isEmpty()) {
            for (String name : stage.getRS()) {
                System.out.println(name);
            }
        }
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        // 1. Tracked in the current commit, changed in the working directory, but not staged
        if (!cur.isBlobMapEmpty()) {
            for (String name : cur.blobMapKeySet()) {
                // Ensure File name exists in CWD.
                if (!worktree.exist(name)) {
                    continue;
                }
                // File id consistency between CWD and Commit.
                String blobHead = cur.blobMapValue(name);
                String blobCwd = sha1(worktree.readFileCWD(name));
                // If addStage contains File and whether id in addStage and CWD are the same.
                boolean reAddedSame = stage.addStageContainKey(name) && stage.addStageGetValue(name).equals(blobCwd);

                if (!reAddedSame && !blobHead.equals(blobCwd)) {
                    System.out.println(name + " (modified)");
                    seen.add(name);
                }
            }
        }

        // 2. Staged for addition, but with different contents with files in the working directory;
        if (!stage.isEmpty()) {
            for (String name : stage.addStageKeySet()) {
                if (seen.contains(name)) {
                    continue;
                }
                if (!worktree.exist(name)) {
                    continue;
                }
                // File id consistency between CWD and Stage area.
                String blobStage = stage.addStageGetValue(name);
                String blobCwd = sha1(worktree.readFileCWD(name));
                if (!blobStage.equals(blobCwd)) {
                    System.out.println(name + " (modified)");
                    seen.add(name);
                }
            }
        }

        // 3. Staged for addition, but deleted in the working directory
        if (!stage.isEmpty()) {
            for (String name : stage.addStageKeySet()) {
                if (seen.contains(name)) {
                    continue;
                }
                if (!worktree.exist(name)) {
                    System.out.println(name + " (deleted)");
                    seen.add(name);
                }
            }
        }

        // 4. Not staged for removal, but tracked in the current commit and deleted from the working directory.
        if (!cur.isBlobMapEmpty()) {
            for (String name : cur.blobMapKeySet()) {
                if (seen.contains(name)) {
                    continue;
                }
                if (!worktree.exist(name) && !stage.removeStageContain(name)) {
                    System.out.println(name + " (deleted)");
                    seen.add(name);
                }
            }
        }
        System.out.println();

        /**
         * files present in the working directory but neither staged for addition nor tracked.
         * This includes files that have been staged for removal, but then re-created without Gitlet’s knowledge.
         */
        System.out.println("=== Untracked Files ===");
        if (!fileNames.isEmpty()) {
            for (String name : fileNames) {
                // If HEAD commit tracked the file ?
                boolean trackedInHead = cur.blobMapContainKey(name);
                // If addStage tracked the file ?
                boolean stagedForAdd = stage.addStageContainKey(name);
                // If both HEAD commit and addStage did not track the file, print the info.
                if (!trackedInHead && !stagedForAdd) {
                    System.out.println(name);
                }
            }
        }
        System.out.println();
    }

    /**
     * Creates a new commit from the current staging area and moves HEAD to it.
     * The new commit has the current HEAD as its first parent and {@code null}
     * as its second parent. Its file snapshot is constructed by copying HEAD's
     * blob map, removing files staged for removal, and then adding/replacing
     * entries from the add-stage. The commit stores the given non-empty message
     * and the current wall-clock timestamp. On success, the stage is cleared and
     * HEAD is advanced to the new commit.
     *
     * @param msg non-empty commit message.
     * @throws GitletException if {@code msg} is empty, or if the staging area has no changes.
     */
    public void commit(String msg) {
        if (msg.isEmpty()) {
            throw error("Please enter a commit message.");
        }

        Stage stage = objectRS.readStage();
        if (stage.isEmpty()) {
            throw error("No changes added to the commit.");
        }

        Commit old = refRS.getHeadCommit();
        TreeMap<String, String> blobmap = old.getBlobsmap();
        TreeMap<String, String> newmap = new TreeMap<>(blobmap);

        for (String name : stage.getRS()) {
            newmap.remove(name);
        }
        newmap.putAll(stage.getAS());

        /** Build the new commit. */
        long now = System.currentTimeMillis();
        Commit creation = new Commit(msg, old.getID(), null, now, newmap);
        String id = creation.getID();

        /** Store the commit file. */
        objectRS.writeCommit(creation);
        refRS.movePointer(id);
        stage.clear();
        objectRS.writeStage(stage);
    }

    /**
     * Creates a new branch pointing to the current HEAD commit.
     * This operation writes a new reference under the local heads namespace with the given
     * {@code branchName}, whose value is the id of the current commit. It does not switch the
     * working branch, nor does it modify the working directory or the staging area.
     *
     * @param branchName the name of the branch to create.
     * @throws GitletException if the name is invalid or a branch with the same name already exists.
     */
    public void createBranch(String branchName) {
        if (branchName == null || branchName.isBlank() || branchName.contains("/") || branchName.contains("\\")) {
            throw error("Invalid branch name.");
        }
        if (refRS.existBranch(branchName)) {
            throw error("A branch with that name already exists.");
        }
        refRS.saveBranch(branchName, refRS.getHeadCommit());
    }

    /**
     * Removes a local branch reference under the heads namespace.
     * Deletes the reference file named {@code branchName} that points to a commit.
     * This operation does not switch branches, does not modify the working directory,
     * and does not touch the staging area or any commit objects. The current branch
     * cannot be removed.
     *
     * @param branchName the name of the branch to remove.
     * @throws GitletException if the name is invalid, the branch does not exist,
     *                         the branch is the current branch, or the deletion fails.
     */
    public void removeBranch(String branchName) {
        if (branchName == null || branchName.isBlank() || branchName.contains("/") || branchName.contains("\\")) {
            throw error("Invalid branch name.");
        }
        if (!refRS.existBranch(branchName)) {
            throw error("A branch with that name does not exist.");
        }

        if (branchName.equals(refRS.readHEAD())) {
            throw error("Cannot remove the current branch.");
        }

        boolean deleteResult = refRS.deleteRootBranch(branchName);
        if (!deleteResult) {
            throw error("Failed to remove branch.");
        }
    }

    /**
     * Overwrites the working-directory copy of {@code name} with the version from
     * the current (HEAD) commit. If the current commit does not track {@code name},
     * or the corresponding blob is missing, prints
     * {@code "File does not exist in that commit."} and exits without changes.
     * This operation does not modify the staging area or move HEAD.
     *
     * @param name file name relative to the working directory.
     * @throws GitletException if the file is not present in the current commit or its blob is missing.
     */
    public void checkHeadFile(String name) {
        Commit head = refRS.getHeadCommit();
        if (!head.blobMapContainKey(name)) {
            throw error("File does not exist in that commit.");
        }
        String fileBlob = head.getValue(name);
        if (!refRS.existBlobFile(fileBlob)) {
            throw error("File does not exist in that commit.");
        }
        byte[] bytes = refRS.readBlobFile(fileBlob);
        worktree.write(name, bytes);
    }

    /**
     * Overwrites the working-directory copy of {@code name} with the version from
     * the uniquely matched commit specified by {@code commitId} (prefix allowed).
     * If no commit matches the id (or the prefix is ambiguous), prints
     * {@code "No commit with that id exists."}. If the matched commit does not
     * track {@code name}, prints {@code "File does not exist in that commit."}.
     * This operation does not modify the staging area or move HEAD.
     *
     * @param commitId full or uniquely identifying prefix of a commit id.
     * @param name file name relative to the working directory.
     * @throws GitletException if the commit cannot be uniquely identified or the file is absent in that commit.
     */
    public void checkCommitFile(String commitId, String name) {
        List<String> matchedCommit = objectRS.matchedCommit(commitId);
        if (matchedCommit.size() != 1) {
            throw error("No commit with that id exists.");
        }

        String fullId = matchedCommit.get(0);
        Commit idCommit = objectRS.getIdCommit(fullId);

        if (!idCommit.blobMapContainKey(name)) {
            throw error("File does not exist in that commit.");
        }
        String blobId = idCommit.getValue(name);
        byte[] content = refRS.readBlobFile(blobId);
        worktree.write(name, content);
    }

    /**
     * Checks out the given branch by making the working directory match the branch head snapshot,
     * moving HEAD to that branch, and clearing the staging area.
     * Refuses to proceed if the branch does not exist, if it is already the current branch,
     * or if there is an untracked file in the way (a file present in the working directory that
     * is not tracked by the current HEAD but would be written by the target branch). During the
     * checkout, files tracked by the target commit are written to the working directory, and files
     * tracked by the current commit but absent in the target commit are removed.
     *
     * @param branchName the name of the branch to switch to.
     * @throws GitletException with one of the exact messages:
     *     "No such branch exists.",
     *     "No need to checkout the current branch.",
     *     "There is an untracked file in the way; delete it, or add and commit it first.",
     *     "File does not exist in that commit."
     */
    public void switchBranch(String branchName) {
        if (!refRS.existBranch(branchName)) {
            throw error("No such branch exists.");
        }

        String head = refRS.readHEAD();
        String branchId = refRS.readBranch(branchName);
        if (branchName.equals(head)) {
            throw error("No need to checkout the current branch.");
        }

        Commit headCommit = refRS.getHeadCommit();
        Commit branchCommit = objectRS.readCommit(branchId);

        Map<String, String> h = headCommit.getBlobsmap();
        Map<String, String> b = branchCommit.getBlobsmap();

        for (String f : b.keySet()) {
            if (worktree.exist(f) && !h.containsKey(f)) {
                throw error("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }

        for (String f : b.keySet()) {
            String blobId = b.get(f);
            if (!refRS.existBlobFile(blobId)) {
                throw error("File does not exist in that commit.");
            }
            byte[] bytes = refRS.readBlobFile(b.get(f));
            worktree.write(f, bytes);
        }

        for (String f : h.keySet()) {
            if (!b.containsKey(f)) {
                worktree.remove(f);
            }
        }

        refRS.saveHeadName(branchName);
        objectRS.writeStage(new Stage());
    }

    /**
     * Merges the given branch into the current branch using the nearest common ancestor (split).
     * Handles fast-forward and ancestor cases, refuses to proceed if the staging area
     * is non-empty or if an untracked file would be overwritten, and otherwise decides per file
     * relative to the split: take given changes, remove deletions from given when current
     * is unchanged, keep current-only changes, or create a conflict file when both sides
     * changed differently. On success, writes a merge commit whose first parent is the current
     * commit and second parent is the given branch head. If any conflicts occurred, prints
     * {@code "Encountered a merge conflict."}.
     *
     * @param givenBranch the name of the branch to merge from.
     * @throws GitletException with exact messages used in implementation:
     *     "A branch with the name does not exist.",
     *     "Cannot merge a branch with itself.",
     *     "You have uncommited changes.",
     *     "Internal error: split not found.",
     *     "There is an untracked file in the way; delete it, or add and commit it first.",
     *     "File does not exist in that commit."
     */
    public void merge(String givenBranch) {
        if (!refRS.existBranch(givenBranch)) {
            throw error("A branch with the name does not exist.");
        }
        String gId = refRS.readBranch(givenBranch);
        String currentBranch = refRS.readHEAD();

        if (givenBranch.equals(currentBranch)) {
            throw error("Cannot merge a branch with itself.");
        }

        // Get current commit, branch commit, and split node commit.
        Commit c = refRS.getHeadCommit();
        Commit g = objectRS.readCommit(gId);
        Commit s = findSplitNode(c, g);
        Stage stage = objectRS.readStage();

        if (s == null) {
            throw error("Internal error: split not found.");
        }

        if (!stage.isEmpty()) {
            throw error("You have uncommited changes.");
        }

        if (s.getID().equals((g.getID()))) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        if (s.getID().equals(c.getID())) {
            this.switchBranch(givenBranch);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        Map<String, String> cBlobsmap = c.getBlobsmap();
        Map<String, String> gBlobsmap = g.getBlobsmap();
        Map<String, String> sBlobsmap = s.getBlobsmap();

        // nameSet contains file names in C G S
        Set<String> nameSet = new HashSet<>();
        nameSet.addAll(cBlobsmap.keySet());
        nameSet.addAll(gBlobsmap.keySet());
        nameSet.addAll(sBlobsmap.keySet());

        boolean hadConflict = false;
        for (String name : nameSet) {
            // blob id of the File name in Commit object.
            String cfb = cBlobsmap.get(name);
            String gfb = gBlobsmap.get(name);
            String sfb = sBlobsmap.get(name);
            MergeAction mergeCondition = judgeAction(cfb, gfb, sfb);

            // ======================================================
            switch (mergeCondition) {
                case REMOVE:
                    worktree.remove(name);
                    stage.addRS(name);
                    break;

                case WRITE_G:
                    Commit headCommit = refRS.getHeadCommit();
                    if (worktree.exist(name) && !headCommit.blobMapContainKey(name)) {
                        throw error("There is an untracked file in the way; delete it, or add and commit it first.");
                    }
                    byte[] content = refRS.readBlobFile(gfb);
                    worktree.write(name, content);
                    stage.addAS(name, gfb);
                    break;

                case CONFLICT:
                    byte[] cContent;
                    byte[] gContent;
                    if (cfb == null) {
                        cContent = new byte[0];
                    } else {
                        if (!refRS.existBlobFile(cfb)) {
                            throw error("The blobId" + cfb + "does not exists.");
                        }
                        cContent = refRS.readBlobFile(cfb);
                    }

                    if (gfb == null) {
                        gContent = new byte[0];
                    } else {
                        if (!refRS.existBlobFile(gfb)) {
                            throw error("The blobId" + gfb + "does not exists.");
                        }
                        gContent = refRS.readBlobFile(gfb);
                    }

                    byte[] conflictMessage = buildConflictMessage(cContent, gContent);
                    String bid = sha1(conflictMessage);
                    refRS.writeBlobFile(bid, conflictMessage);
                    worktree.write(name, conflictMessage);
                    stage.addAS(name, bid);
                    hadConflict = true;
                    break;

                default:
                    break;
            }
        }
        objectRS.writeStage(stage);
        commitMerge(currentBranch, givenBranch, c, g);
        if (hadConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }


    public void reset(String commitId) {
        List<String> matchedCommit = objectRS.matchedCommit(commitId);
        if (matchedCommit.size() != 1) {
            throw error("No commit with that id exists.");
        }

        String fullId = matchedCommit.get(0);
        Commit headCommit = refRS.getHeadCommit();
        Commit givenCommit = objectRS.getIdCommit(fullId);

        Map<String, String> g = givenCommit.getBlobsmap();
        Map<String, String> h = headCommit.getBlobsmap();

        for (String f : g.keySet()) {
            if (worktree.exists(f) && !h.containsKey(f)) {
                throw error("There is an untracked file in the way; delete it, or add and commit it first.");
            }
        }

        for (String f : g.keySet()) {
            String blobId = g.get(f);
            if (!refRS.existBlobFile(blobId)) {
                throw error("File does not exist in that commit.");
            }
            byte[] bytes = refRS.readBlobFile(blobId);
            worktree.write(f, bytes);
        }


        for (String f : h.keySet()) {
            if (!g.containsKey(f)) {
                worktree.remove(f);
            }
        }

        refRS.movePointer(commitId);
        objectRS.writeStage(new Stage());
    }


    public void addRemote(String name, String path) {
        if (refRS.existRemote(name)) {
            throw error("A remote with that name already exists.");
        }

        String absPath  = refRS.normalizeToAbsoluteGiletPath(path);
        refRS.writeRemote(name, absPath);
    }


    // delete methods in delteRemoteFile is rootRestrictDelete, it may occur some error.
    // We can check this after submission.
    public void removeRemote(String name) {
        if (!refRS.existRemote(name)) {
            throw error("A remote with that name does not exist.");
        }
        refRS.deleteRemoteFile(name);
    }


    public void push(String remoteName, String remoteBranchName) {
        // Get the absPath
        String remotePath = refRS.readRemotePathOrNull(remoteName);
        if (remotePath == null) {
            throw error("A remote with that name does not exist.");
        }
        File remoteGitlet = new File(remotePath);
        if (!remoteGitlet.isDirectory() || !remotePath.contains(".gitlet")) {
            throw error("Remote directory not found.");
        }

        RefRS rRefs = new FileRefRS(new File(remotePath));
        ObjectRS rObs = new FileObjectRS(new File(remotePath));
        Commit localHead = refRS.getHeadCommit();
        Commit remoteHead = rRefs.getHeadCommit();

        String localHeadId = localHead.getID();
        String remoteHeadId = remoteHead.getID();

        Set<String> localAncestorId = getAncestor(objectRS, localHeadId);
        if (!isFastForward(localAncestorId, remoteHeadId)) {
            throw error("Please pull dwn remote changes before pushing.");
        }

        Set<String> remoteAncestorId = getAncestor(objectRS, remoteHeadId);
        Set<String> delta = getAncestorDelta(localAncestorId, remoteAncestorId);
        List<String> sortedDelta = this.topoByDfsPstReverse(delta, localHeadId);
        Set<String> seenBlobIds = new HashSet<>();

        for (String cId : sortedDelta) {
            Commit deltaCommit = objectRS.readCommit(cId);
            TreeMap<String, String> blobsmap = deltaCommit.getBlobsmap();

            for (String fileName : blobsmap.keySet()) {
                String blobId = blobsmap.get(fileName);
                if (seenBlobIds.add(blobId) || !rRefs.existBlobFile(blobId)) {
                    byte[] bytes = refRS.readBlobFile(blobId);
                    rRefs.writeBlobFile(blobId, bytes);
                }
            }

            if (!rObs.existCommit(cId)) {
                rObs.writeCommit(deltaCommit);
            }
        }
        refRS.saveBranch(remoteBranchName, localHead);
    }

    public void fetch(String remoteName, String remoteBranchName) {
        String remotePath = refRS.readRemotePathOrNull(remoteName);
        RefRS rRef = new FileRefRS(new File(remotePath));
        ObjectRS rObs = new FileObjectRS(new File(remotePath));
        File remoteGitlet = new File(remotePath);

        if (!remoteGitlet.isDirectory() || !remotePath.contains(".gitlet")) {
            throw error("Remote directory not found.");
        }

        if (!rRef.existBranch(remoteBranchName)) {
            throw error("That remote does not have that branch");
        }

        String remoteHeadId = rRef.readBranch(remoteBranchName);
        Set<String> remoteAncestorId = getAncestor(rObs, remoteHeadId);
        List<String> sortedAncestorId = topoByDfsPstReverse(remoteAncestorId, remoteHeadId);
        Set<String> seenBlobIds = new HashSet<>();

        for (String cId : sortedAncestorId) {
            Commit deltaCommit = rObs.readCommit(cId);
            TreeMap<String, String> blobsmap = deltaCommit.getBlobsmap();

            for (String fileName : blobsmap.keySet()) {
                String blobId = blobsmap.get(fileName);
                if (seenBlobIds.add(blobId) || !refRS.existBlobFile(blobId)) {
                    byte[] bytes = rRef.readBlobFile(blobId);
                    refRS.writeBlobFile(blobId, bytes);
                }
            }

            if (!objectRS.existCommit(cId)) {
                objectRS.writeCommit(deltaCommit);
            }
        }

        refRS.saveBranch(remoteBranchName, rObs.readCommit(remoteHeadId));
    }


    public void pull(String remoteName, String remoteBranchName) {
        fetch(remoteName, remoteBranchName);

    }


    private List<String> topoByDfsPstReverse(Set<String> delta, String headId) {
        List<String> post = new ArrayList<>();
        Set<String> visit = new HashSet<>();
        dfsPost(headId, delta, visit, post);
        return post;
    }

    private void dfsPost(String id, Set<String> delta, Set<String> visit, List<String> post) {
        if (id == null || !delta.contains(id) || !visit.add(id)) {
            return;
        }
        Commit c = objectRS.readCommit(id);
        String firstParentId = c.getParentID();
        String secondParentId = c.getSecondParentID();
        dfsPost(firstParentId, delta, visit, post);
        dfsPost(secondParentId, delta, visit, post);
        post.add(id);
    }


    private Set<String> getAncestor(ObjectRS objRS, String startCommitId) {
        Set<String> out = new HashSet<>();
        if (startCommitId == null) {
            return out;
        }
        Deque<String> deque = new ArrayDeque<>();
        deque.addLast(startCommitId);
        out.add(startCommitId);

        while(!deque.isEmpty()) {
            String commitId = deque.removeFirst();
            Commit cur = objRS.readCommit(commitId);
            String p1 = cur.getParentID();
            String p2 = cur.getSecondParentID();

            // visit is regarded out
            if (p1 != null && out.add(p1)) {
                deque.addLast(p1);
            }

            if (p2 != null && out.add(p2)) {
                deque.addLast(p2);
            }
        }
        return out;
    }

    private boolean isFastForward(Set<String> ancestorLocal, String remoteCommitId) {
        return ancestorLocal.contains(remoteCommitId);
    }

    private Set<String> getAncestorDelta(Set<String> ancestorLocal, Set<String> ancestorRemote) {
        Set<String> delta = new HashSet<>();
        for (String id : ancestorLocal) {
            if (!ancestorRemote.contains(id)) {
                delta.add(id);
            }
        }
        return delta;
    }

    private void commitMerge(String currentBranch, String givenBranch,
                             Commit C, Commit G) {
        /** Obtain the stage area and calculate the difference */
        Stage stage = objectRS.readStage();
        TreeMap<String, String> newmap = new TreeMap<>(C.getBlobsmap());
        for (String name : stage.getRS()) {
            newmap.remove(name);
        }
        newmap.putAll(stage.getAS());

        long now = System.currentTimeMillis();
        String msg = String.format("Merged %s into %s.", givenBranch, currentBranch);
        Commit mergeCommit = new Commit(msg, C.getID(), G.getID(), now, newmap);
        String id = mergeCommit.getID();
        objectRS.writeCommit(mergeCommit);
        stage.clear();
        objectRS.writeStage(stage);
        refRS.movePointer(id);
    }

    private byte[] buildConflictMessage(byte[] cContent, byte[] gContent) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write("<<<<<<< HEAD\n".getBytes(StandardCharsets.UTF_8));
            bos.write(cContent);
            bos.write("=======\n".getBytes(StandardCharsets.UTF_8));
            bos.write(gContent);
            bos.write(">>>>>>>\n".getBytes(StandardCharsets.UTF_8));
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private MergeAction judgeAction(String cfb, String gfb, String sfb) {
        if (!Objects.equals(gfb, sfb) && Objects.equals(cfb, sfb) && isBlankOrNull(gfb)) {
            return MergeAction.REMOVE;
        }
        if (Objects.equals(cfb, sfb) && !isBlankOrNull(gfb) && !Objects.equals(gfb, sfb)) {
            return MergeAction.WRITE_G;
        }
        if (isBlankOrNull(sfb) && isBlankOrNull(cfb) && !isBlankOrNull(gfb)) {
            return MergeAction.WRITE_G;
        }
        if (!Objects.equals(cfb, gfb) && !Objects.equals(cfb, sfb) && !Objects.equals(sfb, gfb)) {
            return MergeAction.CONFLICT;
        }
        return MergeAction.KEEP;
    }

    private Set<String> getAncestors(Commit C) {
        Set<String> anc = new HashSet<>();
        Set<String> visited = new HashSet<>();
        anc.add(C.getID());
        objectRS.iterateNodes(C, anc, visited);
        return anc;
    }

    /**
     * Find the latest split node of current branch C and given branch G.
     */
    private Commit findSplitNode(Commit C, Commit G) {
        Set<String> anc = getAncestors(C);
        Set<String> visited = new HashSet<>();
        Deque<String> deque = new ArrayDeque<>();
        deque.addFirst(G.getID());
        visited.add(G.getID());

        while (!deque.isEmpty()) {
            String out = deque.removeFirst();
            Commit cur = objectRS.readCommit(out);

            // If the current commit has been iterated through, then return the splitNode
            if (anc.contains(out)) {
                return cur;
            }

            // Get the two parent node of the current.
            String firstParent = cur.getParentID();
            String secondParent = cur.getSecondParentID();

            // If firstParent exist and it has not been iterated through.
            if (firstParent != null && visited.add(firstParent)) {
                deque.addLast(firstParent);
            }

            // If secondParent exist and it has not been iterated through.
            if (secondParent != null && visited.add(secondParent)) {
                deque.addLast(secondParent);
            }
        }
        return null;
    }
}
