package gitlet.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class Stage implements Serializable {
    private HashMap<String, String> addStage;
    private HashSet<String> removeStage;

    public Stage() {
        this.addStage = new HashMap<>();
        this.removeStage = new HashSet<>();
    }

    public void clear() {
        this.addStage = new HashMap<>();
        this.removeStage = new HashSet<>();
    }

    public boolean isEmpty() {
        return addStage.isEmpty() && removeStage.isEmpty();
    }

    public HashMap<String, String> getAS() {
        return this.addStage;
    }

    public HashSet<String> getRS() {
        return this.removeStage;
    }

    public void addAS(String name, String id) {
        addStage.put(name, id);
    }

    public void addRS(String name) {
        removeStage.add(name);
    }

    public void removeAS(String name) {
        addStage.remove(name);
    }

    public void removeRS(String name) {
        removeStage.remove(name);
    }

    public String addStageGetValue(String key) {
        return this.addStage.get(key);
    }

    public boolean addStageContainKey(String name) {
        return addStage.containsKey(name);
    }

    public boolean removeStageContain(String name) {
        return removeStage.contains(name);
    }

    public Set<String> addStageKeySet() {
        return addStage.keySet();
    }
}
