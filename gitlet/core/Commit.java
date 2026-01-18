package gitlet.core;


import gitlet.Utils;

import java.io.Serializable;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

/**
 * Represents a gitlet commit object.
 * does at a high level.
 *
 * @author Yunhao Zhu
 */
public class Commit implements Serializable {
    /**
     *
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */
    /**
     * The meta-data of the current commit.
     */
    private String message;
    private String commitID;
    private String parentID;
    private String secondParentID;
    private long timeMillis;

    /**
     * The file and blob data
     */
    private TreeMap<String, String> blobsmap;

    public Commit() {
        this.message = "initial commit";
        this.parentID = null;
        this.secondParentID = null;
        this.timeMillis = 0;
        this.blobsmap = new TreeMap<>();
        this.commitID = Utils.computeCommitID(message, timeMillis, parentID, secondParentID, blobsmap);
    }

    public Commit(String message, String parentID, String secondParentID, long timeMillis, TreeMap<String, String> blobsmap) {
        this.message = message;
        this.parentID = parentID;
        this.secondParentID = secondParentID;
        this.timeMillis = timeMillis;
        this.blobsmap = blobsmap;
        this.commitID = Utils.computeCommitID(message, timeMillis, parentID, secondParentID, blobsmap);
    }

    public String getValue(String key) {
        return this.blobsmap.get(key);
    }

    public boolean isBlobMapEmpty() {
        return this.blobsmap.isEmpty();
    }


    public String blobMapValue(String key) {
        return this.blobsmap.get(key);
    }

    public Set<String> blobMapKeySet() {
        return this.blobsmap.keySet();
    }

    public boolean blobMapContainKey(String name) {
        return blobsmap.containsKey(name);
    }

    public String getID() {
        return this.commitID;
    }

    public TreeMap<String, String> getBlobsmap() {
        return this.blobsmap;
    }

    public String getMessage() {
        return this.message;
    }

    public String getParentID() {
        return this.parentID;
    }

    public String getSecondParentID() {
        return this.secondParentID;
    }


    public void printMergeMessage() {
        Date d = new Date(this.timeMillis);
        String line = String.format(Locale.ENGLISH, "%1$ta %1$tb %1$te %1$tT %1$tY %1$tz", d);
        String mergeInfor = String.format("Merge: %s %s", this.parentID.substring(0, 7), this.secondParentID.substring(0, 7));
        System.out.println("===");
        System.out.print("commit ");
        System.out.print(this.getID());
        System.out.print(mergeInfor);
        System.out.println();
        System.out.print("Date: ");
        System.out.print(line);
        System.out.println();
        System.out.println(this.getMessage());
        System.out.println();
    }

    public void printMessage() {
        Date d = new Date(this.timeMillis);
        String line = String.format(Locale.ENGLISH, "%1$ta %1$tb %1$te %1$tT %1$tY %1$tz", d);
        System.out.println("===");
        System.out.print("commit ");
        System.out.print(this.getID());
        System.out.println();
        System.out.print("Date: ");
        System.out.print(line);
        System.out.println();
        System.out.println(this.getMessage());
        System.out.println();
    }
}
