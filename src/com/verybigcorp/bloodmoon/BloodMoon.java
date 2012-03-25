package com.verybigcorp.bloodmoon;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class BloodMoon extends JavaPlugin {
	ItemStack[] possibleDrops;
	Timer bloodmoon, tp;
	Logger log;
	List<Object> bMoon;
	List<Object> worlds;
	Listener l;
	boolean hasRolled = false;
	Connection conn;
	Statement stat;
	BloodMoonTimer tim;
	@Override
	public void onDisable() {
		// TODO Auto-generated method stub
		if(bloodmoon != null)
			bloodmoon.cancel();
		if(tp != null)
			tp.cancel();
		log("v"+this.getDescription().getVersion() + " is now disabled.");
	}

	@Override
	public void onEnable() {
		// TODO Auto-generated method stub
		getConfig().set("bloodMoonProbability", getConfig().getInt("bloodMoonProbability") == 0 ? 20 : getConfig().getInt("bloodMoonProbability"));
		getConfig().set("bloodMoonDifficulty", getConfig().getInt("bloodMoonDifficulty") == 0 ? 1 : getConfig().getInt("bloodMoonDifficulty"));
		getConfig().set("nonBloodMoonDifficulty", getConfig().getInt("nonBloodMoonDifficulty") == 0 ? 1 : getConfig().getInt("nonBloodMoonDifficulty"));
		getConfig().set("damageReduction", getConfig().getInt("damageReduction") == 0 ? 2 : getConfig().getInt("damageReduction"));
		getConfig().set("endermen", getConfig().getBoolean("endermen"));
		saveConfig();
		try {
			Class.forName("org.sqlite.JDBC");
			File d = new File(getDataFolder().getCanonicalPath()+"/bloodmoon.db");
			d.createNewFile();
			conn = DriverManager.getConnection("jdbc:sqlite:"+ getDataFolder().getCanonicalPath()+"/bloodmoon.db");
			conn.setAutoCommit(true);
			stat = conn.createStatement();
			create_tablesConnect();
		} catch (SQLException e) {
			log("error in the sql: " + e.getMessage());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			log("error in the creation of the database! " + e.getMessage());
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		possibleDrops = new ItemStack[12];
		possibleDrops[0] = new ItemStack(Material.DIAMOND, 1);
		possibleDrops[1] = new ItemStack(Material.IRON_INGOT, 1);
		possibleDrops[2] = new ItemStack(Material.GOLD_INGOT, 1);
		possibleDrops[3] = new ItemStack(Material.APPLE, 1);
		possibleDrops[4] = new ItemStack(Material.COAL, 1);
		possibleDrops[5] = new ItemStack(Material.NETHERRACK, 1);
		possibleDrops[6] = new ItemStack(Material.BREAD, 1);
		possibleDrops[7] = new ItemStack(Material.GOLD_NUGGET, 1);
		possibleDrops[8] = new ItemStack(Material.CLAY, 1);
		ItemStack villager = new ItemStack(Material.MONSTER_EGG, 1);
		villager.setDurability((short) 120);
		possibleDrops[9] = villager;
		possibleDrops[10] = new ItemStack(Material.MAGMA_CREAM, 1);
		possibleDrops[11] = new ItemStack(Material.MELON, 1);
		bloodmoon = new Timer();
		tp = new Timer();
		tim = new BloodMoonTimer(this);
		bloodmoon.schedule(tim, 200, 200);
		l = new EventsListener(this);
		log("v"+this.getDescription().getVersion() + " is now enabled.");
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		if(cmd.getName().equalsIgnoreCase("bmoon") && !(sender instanceof Player)){
			if(args.length == 1){
				if(args[0].equalsIgnoreCase("ls")){
					try {
						if(getWorlds().size() == 0)
							sender.sendMessage("No blood moon worlds currently exist.");
						for(String s : getWorlds())
							sender.sendMessage("Blood moon world: \"" + s + "\"");
					} catch (SQLException e) {
						
					}
				}
			} else if(args.length == 2){
				if(args[0].equalsIgnoreCase("sim")){
					try {
						if(getWorlds().contains(args[1])){
							getServer().getWorld(args[1]).setTime(12000);
							//tp.schedule(new TPTimer(this), 0, 1000);
							for(Player p : getServer().getWorld(args[1]).getPlayers())
								p.sendMessage(ChatColor.RED + "The blood moon is rising.");
							try {
								setBloodMoon(args[1], true);
							} catch (SQLException e) {
								
							}
							saveConfig();
							reloadConfig();
							return true;
						}
					} catch (SQLException e) {
						
					}
				} else if(args[0].equalsIgnoreCase("is")){
					String not = "";
					try {
						if(!isBloodMoon(args[1]))
							not = "not ";
					} catch (SQLException e) {
						
					}
					sender.sendMessage("It is "+not+"currently a blood moon in \"" + args[1] + "\"");
				} else if(args[0].equalsIgnoreCase("addworld")){
					try {
						if(addWorld(args[1]))
							sender.sendMessage("\"" + args[1] + "\" already exists in the blood moon list!");
						else {
							sender.sendMessage("\"" + args[1] + "\" was successfully added to the blood moon list!");
							tim.addWorld(args[1]);
						}
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						sender.sendMessage("Error in adding world: "+e.getMessage());
					}
				} else if(args[0].equalsIgnoreCase("removeworld")){
					try {
						if(removeWorld(args[1]) == 0)
							sender.sendMessage("\"" + args[1] + "\" does not exist in the blood moon list!");
						else {
							tim.removeWorld(args[1]);
							sender.sendMessage("\"" + args[1] + "\" was successfully removed from the blood moon list!");
						}
					} catch (SQLException e) {
						// TODO Auto-generated catch block
						sender.sendMessage("Error in removing world: "+e.getMessage());
					}
				} else if(args[0].equalsIgnoreCase("resetextension")){
					try {
						if(resetProbability(args[1]) == 0)
							sender.sendMessage("\"" + args[1] + "\" does not exist in the blood moon list!");
						else
							sender.sendMessage("\"" + args[1] + "\" had its probability reset successfully!");
					} catch (SQLException e) {
						sender.sendMessage("Error in resetting the probability: "+e.getMessage());
					}
				}
			}
		}
		return false;
	}

	
	public double findBestY(Location l){
		while(l.getWorld().getBlockAt(l) == null || !l.getWorld().getBlockAt(l).getType().equals(Material.AIR) && !l.getWorld().getBlockAt(l.getBlockX(), l.getBlockY()+1, l.getBlockZ()).getType().equals(Material.AIR))
			l.setY(l.getBlockY()+1);
		return l.getY();
	}
	
	public class EventsListener implements Listener {
		BloodMoon p;
		public EventsListener(BloodMoon plugin){
			p = plugin;
			plugin.getServer().getPluginManager().registerEvents(this, plugin);
		}
		
		@EventHandler
		public void onEntityDamage(EntityDamageEvent e){
		    if(e.isCancelled()) return;
		   
		    try {
				if(isBloodMoon(e.getEntity().getWorld().getName()) && !(e.getEntity() instanceof Player)){
					e.setDamage(e.getDamage()-getConfig().getInt("damageReduction"));
				}
			} catch (SQLException e1) {

			}
		}
		
		@EventHandler
		public void onEntityDeath(EntityDeathEvent e){
			try {
				if(isBloodMoon(e.getEntity().getWorld().getName()) && e.getEntity() instanceof Monster){
					int roll = (int)(Math.random()*2)+1;
					if(roll == 1){
						ItemStack drop = possibleDrops[(int)(Math.random()*possibleDrops.length)];
						for(int x = 0; x < (int)(Math.random()*5)+1; x++)
							e.getEntity().getWorld().dropItem(e.getEntity().getLocation(), drop);
					}
				}
			} catch (SQLException e1) {
				
			}
		}
		
		@EventHandler
		public void onCreatureSpawn(CreatureSpawnEvent e){
			try {
				if(e.getEntityType().equals(EntityType.ENDERMAN) && !p.getConfig().getBoolean("endermen"))
						return;
				try {
					if(isBloodMoon(e.getEntity().getWorld().getName()) && e.getEntity() instanceof Creeper)
						((Creeper)e.getEntity()).setPowered(true);
					if(isBloodMoon(e.getEntity().getWorld().getName()) && !e.getSpawnReason().equals(SpawnReason.CUSTOM) && e.getEntity() instanceof Monster){
						int newNumber = (int)(Math.random()*10)+3;
						Player p = null;
						List<Entity> near = e.getEntity().getNearbyEntities(300, 300, 300);
						for(int x = 0; x < near.size(); x++)
							if(near.get(x) instanceof Player){
								p = (Player) near.get(x);
							}
						for(int x = 0; x < newNumber; x++){
							Monster m = ((Monster) e.getEntity().getWorld().spawnCreature(e.getLocation(), e.getEntityType()));
							m.setTarget((LivingEntity)p);
							m.damage(0);
							m.teleport(within20Blocks(m.getLocation(), m.getTarget().getLocation()));
						}
						if(p != null)
							((Monster)e.getEntity()).setTarget(p);
					}
				} catch(SQLException ex){
					
				}
			} catch (Exception e1){
				
			}
		}
	}
	
	public class BloodMoonTimer extends TimerTask {
		BloodMoon plugin;
		List<World> ws;
		public BloodMoonTimer(BloodMoon plugin){
			this.plugin = plugin;
			ws = new ArrayList<World>();
			try {
				for(String s : getWorlds())
					if(getServer().getWorld(s) != null)
						addWorld(s);
			} catch (SQLException e) {
				
			}
		}
		
		public void addWorld(String w){
			ws.add(getServer().getWorld(w));
		}
		
		public void removeWorld(String w){
			ws.remove(ws.indexOf(getServer().getWorld(w)));
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			for(World w : ws){
				try {
					if(w.getTime() == 12000 && isBloodMoon(w.getName()) == false){
						int roll = (int)(Math.random()*plugin.getConfig().getInt("bloodMoonProbability"))+1;
						if(roll == 1){
							for(Player p : w.getPlayers())
								p.sendMessage(ChatColor.RED + "The blood moon is rising.");
							w.setDifficulty(Difficulty.getByValue(plugin.getConfig().getInt("bloodMoonDifficulty")));
							setBloodMoon(w.getName(), true);
							//tp.schedule(new TPTimer(plugin), 0, 1000);
						}
					} else if(w.getTime() < 12000) {
						if(isBloodMoon(w.getName())){
							for(Player p : w.getPlayers())
								p.sendMessage("The blood moon is over.");
							w.setDifficulty(Difficulty.getByValue(plugin.getConfig().getInt("nonBloodMoonDifficulty")));
							setBloodMoon(w.getName(), false);
							hasRolled = false;
							//tp.cancel();
							resetProbability(w.getName());
						}
					} else if(w.getTime() > 22000 && isBloodMoon(w.getName()) && !hasRolled){
						int rollExtension = (int)(Math.random()*getExtProbability(w.getName()))+1;
						if(rollExtension == 1){
							decrementProbability(w.getName());
							w.setTime(13800);
							if(getExtProbability(w.getName()) != 1)// Only fire when the probability is higher than one.
								for(Player p : w.getPlayers())
									p.sendMessage(ChatColor.RED + "The blood moon has been extended" + (getExtProbability(w.getName()) == 1 ? " indefinitely." : "."));
						} else {
							
						}
						hasRolled = true;
					}
				} catch(SQLException e){
					
				}
			}
		}
		
	}
	
	public int getDistance(Location l, Location l2){
		return (int) l.distance(l2);
	}
	
	public int dX(Location e, Location p){
		return (p.getBlockX() < e.getBlockX()) ? (e.getBlockX() - p.getBlockX()) : (p.getBlockX() - e.getBlockX());
	}
	
	public int dY(Location e, Location p){
		return (p.getBlockY() < e.getBlockY()) ? (e.getBlockY() - p.getBlockY()) : (p.getBlockY() - e.getBlockY());
	}
	
	public int dZ(Location e, Location p){
		return (p.getBlockZ() < e.getBlockZ()) ? (e.getBlockZ() - p.getBlockZ()) : (p.getBlockZ() - e.getBlockZ());
	}
	
	public Location within20Blocks(Location l, Location target){
		while(getDistance(l, target) > 15)
			l = new Location(l.getWorld(), target.getBlockX() - dX(l, target)/5, findBestY(target), target.getBlockZ()-dZ(l, target)/5);
		return l;
	}
	
	public void log(String s){
		if(log == null)
			log = Logger.getLogger("Minecraft");
		log.info("[BloodMoon] "+s);
	}

	// SQLite functions
	public void create_tablesConnect() throws SQLException {
        stat.executeUpdate("CREATE TABLE IF NOT EXISTS worlds (world TEXT, bloodmoon boolean, extProb integer);");
    }
	
	public boolean addWorld(String w) throws SQLException{
		if(!getWorlds().contains(w)){
			PreparedStatement sql = conn.prepareStatement("INSERT INTO worlds (world, bloodmoon, extProb) VALUES (?,?,?);");
			sql.setString(1, w);
			sql.setBoolean(2, false);
			sql.setInt(3, 10);
			sql.execute();
			return false;
		}
		return true;
	}
	
	public int removeWorld(String w) throws SQLException {
		PreparedStatement sql = conn.prepareStatement("DELETE FROM worlds WHERE world=?;");
		sql.setString(1, w);
		return sql.executeUpdate();
	}
	
	public List<String> getWorlds() throws SQLException {
		List<String> l = new ArrayList<String>();
		String select = "SELECT * FROM worlds;";
		ResultSet res = stat.executeQuery(select);
		while(res.next()){
			l.add(res.getString("world"));
		}
		res.close();
		return l;
	}
	
	public boolean isBloodMoon(String w) throws SQLException {
		PreparedStatement sql = conn.prepareStatement("SELECT bloodmoon FROM worlds WHERE world=?;");
		sql.setString(1, w);
		ResultSet res = sql.executeQuery();
		boolean ret = false;
		while(res.next()){
			if(res.getBoolean("bloodmoon") == true)
				ret = true;
		}
		res.close();
		return ret;
	}
	
	public void setBloodMoon(String w, boolean b) throws SQLException{
		PreparedStatement sql = conn.prepareStatement("UPDATE worlds SET bloodmoon=? WHERE world=?;");
		sql.setBoolean(1, b);
		sql.setString(2, w);
		sql.executeUpdate();
	}
	
	public void decrementProbability(String w) throws SQLException {
		PreparedStatement sql = conn.prepareStatement("UPDATE worlds SET extProb=? WHERE world=?");
		sql.setInt(1, getExtProbability(w)-1);
		sql.setString(2, w);
		sql.executeUpdate();
	}
	
	public int getExtProbability(String w) throws SQLException {
		PreparedStatement sql = conn.prepareStatement("SELECT extProb FROM worlds WHERE world=?;");
		sql.setString(1, w);
		ResultSet r = sql.executeQuery();
		while(r.next()){
			return r.getInt("extProb");
		}
		return -1;
	}
	
	public int resetProbability(String w) throws SQLException {
		PreparedStatement sql = conn.prepareStatement("UPDATE bloodmoon SET extProb=? WHERE world=?");
		sql.setInt(1, 10);
		sql.setString(2, w);
		return sql.executeUpdate();
	}
}
