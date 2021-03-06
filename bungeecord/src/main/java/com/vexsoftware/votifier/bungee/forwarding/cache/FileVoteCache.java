package com.vexsoftware.votifier.bungee.forwarding.cache;

import com.google.common.io.Files;
import com.vexsoftware.votifier.model.Vote;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class FileVoteCache extends MemoryVoteCache {

    private final File cacheFile;
    private final ScheduledTask saveTask;

    public FileVoteCache(int initialMemorySize, final Plugin plugin, File cacheFile) throws IOException {
        super(initialMemorySize);
        this.cacheFile = cacheFile;
        load();

        saveTask = ProxyServer.getInstance().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    save();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Unable to save cached votes, votes will be lost if you restart.", e);
                }
            }
        }, 3, 3, TimeUnit.MINUTES);
    }

    private void load() throws IOException {
        // Load the cache from disk
        JSONObject object;
        try (BufferedReader reader = Files.newReader(cacheFile, StandardCharsets.UTF_8)) {
            object = new JSONObject(new JSONTokener(reader));
        } catch (FileNotFoundException e) {
            object = new JSONObject();
        }

        // Deserialize all votes contained
        for (Object server : object.keySet()) {
            JSONArray voteArray = object.getJSONArray(((String) server));
            List<Vote> votes = new ArrayList<>(voteArray.length());
            for (int i = 0; i < voteArray.length(); i++) {
                JSONObject voteObject = voteArray.getJSONObject(i);
                votes.add(new Vote(voteObject));
            }
            voteCache.put(((String) server), votes);
        }
    }

    public void save() throws IOException {
        cacheLock.lock();
        JSONObject votesObject = new JSONObject();
        try {
            // Create a copy of the votes.
            for (Map.Entry<String, Collection<Vote>> entry : voteCache.entrySet()) {
                JSONArray array = new JSONArray();
                for (Vote vote : entry.getValue()) {
                    array.put(vote.serialize());
                }
                votesObject.put(entry.getKey(), array);
            }
        } finally {
            cacheLock.unlock();
        }

        try (BufferedWriter writer = Files.newWriter(cacheFile, StandardCharsets.UTF_8)) {
            votesObject.write(writer);
        }
    }

    public void halt() throws IOException {
        saveTask.cancel();
        save();
    }

}
