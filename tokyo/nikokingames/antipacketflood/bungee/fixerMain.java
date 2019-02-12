package tokyo.nikokingames.antipacketflood.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Charsets;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

public class fixerMain extends Plugin implements Listener{
	
	private static final  Map<Connection, Long> PACKET_USAGE = new ConcurrentHashMap<>();
	private static final Map<Connection, AtomicInteger> CHANNELS_REGISTERED = new ConcurrentHashMap<>();
	
	private String Command, KickMessage;
	
	@Override
	public void onEnable() {
		Configuration configuration;
		
		try {
			configuration = loadConfiguration();
			
		}catch(IOException ignored) {
			configuration = null;
		}
		if(configuration == null) {
			getLogger().severe("Missing configuration file.");
			return;
		}
		
		Command = configuration.getString("Command");
		KickMessage = configuration.getString("KickMessage");
		getProxy().getPluginManager().registerListener(this, this);
	}
	
	@EventHandler
	public void onPacket(PluginMessageEvent e) {
		String name = e.getTag();
		if(!"MC|BSIGN".equals(name) && !"MC|BEDIT".equals(name) && !"REGISTER".equals(name))
			return;
		
		Connection connection = e.getSender();
		if(!(connection instanceof ProxiedPlayer))
			return;
		try {
			if("REGISTER".equals(name)) {
				if(!CHANNELS_REGISTERED.containsKey(connection)) CHANNELS_REGISTERED.put(connection, new AtomicInteger());
				
				for(int i = 0; i < new String(e.getData(), Charsets.UTF_8).split("\0").length; i++) 
					if(CHANNELS_REGISTERED.get(connection).incrementAndGet() > 125) throw new IOException("->CHANNLES<-");
			} else {
				if(elapsed(PACKET_USAGE.getOrDefault(connection, -1L), 100L)) {
					PACKET_USAGE.put(connection, System.currentTimeMillis());
				} else {
					throw new IOException("Packet flood detected.");
				}
			}
		} catch(Throwable ex) {
			connection.disconnect(TextComponent.fromLegacyText(KickMessage));
			
			if(Command != null) 
				getProxy().getPluginManager().dispatchCommand(getProxy().getConsole(),
						Command.replace("%name%", ((ProxiedPlayer) connection).getName()));
			
				getLogger().warning(connection.getAddress() + " You tried to custom packet sending: " + ex.getMessage());
				e.setCancelled(true);
		}
	}
	
	@EventHandler
	
	public void onDisconnect(PlayerDisconnectEvent event) {
		CHANNELS_REGISTERED.remove(event.getPlayer());
		PACKET_USAGE.remove(event.getPlayer());
	}
	
	private Configuration loadConfiguration() throws IOException{
		if(!getDataFolder().exists()) getDataFolder().mkdir();
		
		File file = new File(getDataFolder(), "config.yml");
		if(!file.exists()) {
			try(InputStream in = getResourceAsStream("config.yml")){
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
		
		return ConfigurationProvider.getProvider(YamlConfiguration.class).load(file);
	}
	
	private boolean elapsed(long from, long required) {
		return from == -1L || System.currentTimeMillis() - from > required;
	}

}
