import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.*;
import org.jsoup.nodes.*;

public class DataPlugin {
	private static Map<String, Double> sortByValue(Map<String, Double> unsortMap) {
		// Sort Map by value
		// Based on https://www.mkyong.com/java/how-to-sort-a-map-in-java/
		LinkedList<Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
			public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
				return -(o1.getValue()).compareTo(o2.getValue());
			}
		});
		Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
		for (Map.Entry<String, Double> entry : list) {
			sortedMap.put(entry.getKey(), entry.getValue());
		}
		return sortedMap;
	}

	public static void main_ATP(String[] args)
			throws ErrorThrower, SQLException, IOException, ParseException, JSONException {

		String[] ids = { "520" };
		//String[] ids = {"520","540","560","580"};
		//String[] year = {  "2013", "2014", "2015", "2016", "2017", "2018" };
		String[] year = {"2017"};
		BufferedWriter writerH = new BufferedWriter(new FileWriter("out/main/ATP2017.csv"));
		CSVPrinter csvPrinterH = new CSVPrinter(writerH,
				CSVFormat.DEFAULT.withHeader("Param", "Value", "Param2", "Previous Y", "Current Y", "Next Y"));

		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		for (int y = 0; y < year.length; y++) {
			for (int i = 0; i < ids.length; i++) {
				TennisData TData = new TennisData(ids[i], year[y], "ATP",true);
				HashMap<String, Double> misfortune_map = TData.getMisfortune_map();
				Map<String, Double> misfortune_sorted = sortByValue(misfortune_map);
				int[] misfortune_count = TData.getMisfortune_count();
				ArrayList<Player> players = TData.getPlayers();
				System.out.println("Season of " + year[y]);
				csvPrinterH.printRecord("Slams " + year[y] + " " + "ATP", "---------", " ", " ", " ", " ");
				int tcount = 0;
				for (int j = 0; j < misfortune_count.length; j++) {
					csvPrinterH.printRecord("Misfortune of " + j, misfortune_count[j], " ", " ", " ", " ", " ");
					tcount +=misfortune_count[j];
				}
				csvPrinterH.printRecord("Total misfortunates", tcount, " ", " ", " ", " ", " ");
				Double inter = (TData.getCommon_player_count()[3]*100.0)/128.0;
				csvPrinterH.printRecord("Intersection of players", TData.getCommon_player_count()[3] +" ("+df.format(inter)+")", " ", " ", " ", " ", " ");
				
				
				
				for (Map.Entry<String, Double> e : misfortune_sorted.entrySet()) {
					if (e.getValue() > 2) {
						int current_misfortunate = Integer.parseInt(e.getKey());
						csvPrinterH.printRecord("Misfortune of " + e.getValue(),
								players.get(current_misfortunate).getName(), " ", " ", " ", "");
						int current = Integer.parseInt(year[y]);
						int previous = current - 1;
						int next = current + 2;
						Boolean skip = false;
						if (players.get(current_misfortunate).getName().equals("Inigo Cervantes Huegun"))
							players.get(current_misfortunate).setName("Inigo Cervantes");
						if (players.get(current_misfortunate).getName().equals("Taylor Harry Fritz"))
							skip = true;
						if (!skip) {
							String fname = players.get(current_misfortunate).getName();
							System.out.println("\t\t" + fname + " with " + e.getValue());
							fname = current + "-" + fname.replaceAll(" ", "");
							/*
							 * BufferedWriter writerP = new BufferedWriter(new FileWriter("out/main/ATP-" +
							 * fname + ".csv")); CSVPrinter csvPrinterP = new CSVPrinter(writerP,
							 * CSVFormat.DEFAULT.withHeader("Param", "" + previous, "" + current, "" +
							 * next));
							 */
							csvPrinterH.printRecord(" ", " ", "Rank in Jan",
									TData.getRank(players.get(current_misfortunate).getId(), previous),
									TData.getRank(players.get(current_misfortunate).getId(), current),
									TData.getRank(players.get(current_misfortunate).getId(), next));

							URL obj = new URL(
									"https://www.atpworldtour.com/en/-/ajax/playersearch/PlayerUrlSearch?searchTerm="
											+ URLEncoder.encode(players.get(current_misfortunate).getName(), "UTF-8"));
							HttpURLConnection con = (HttpURLConnection) obj.openConnection();
							con.setRequestMethod("GET");
							con.setRequestProperty("User-Agent", "Mozilla/5.0");
							BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
							String inputLine;
							StringBuffer response = new StringBuffer();
							while ((inputLine = in.readLine()) != null) {
								response.append(inputLine);
							}
							in.close();
							JSONObject Resp = new JSONObject(response.toString());
							JSONArray items = Resp.getJSONArray("items");
							JSONObject node = new JSONObject(items.get(0).toString());
							String urlpart = node.get("Value").toString();
							urlpart = urlpart.replace("overview", "player-activity") + "?year=" + previous;

							String[] prizes = new String[3];
							for (int a = 0; a < 3; a++) {
								int now = previous + a;
								urlpart = urlpart.substring(0, urlpart.length() - 4) + "" + now;
								Document doc = Jsoup.connect("https://www.atpworldtour.com" + urlpart).get();
								Element current_prize = doc.select(".stat-value").last();
								//prizes[a] = current_prize.text();
							}
							csvPrinterH.printRecord(" ", " ", "Prizes", prizes[0], prizes[1], prizes[2]);
							int yb = previous - TData.getYearBirth(players.get(current_misfortunate).getId());
							csvPrinterH.printRecord(" ", " ", "Age", yb, yb + 1, yb + 2);
						}
					}
				}
				

			}
			csvPrinterH.flush();
		}

		csvPrinterH.close();
		writerH.close();

	}
	
	
	public static void main(String[] args)
			throws ErrorThrower, SQLException, IOException, ParseException, JSONException {

		String[] ids = { "520" };
		//String[] ids = {"520","540","560","580"};
		//String[] year = {  "2013", "2014", "2015", "2016", "2017", "2018" };
		String[] year = {"2017"};
		BufferedWriter writerH = new BufferedWriter(new FileWriter("out/main/WTA2017.csv"));
		CSVPrinter csvPrinterH = new CSVPrinter(writerH,
				CSVFormat.DEFAULT.withHeader("Param", "Value", "Param2", "Previous Y", "Current Y", "Next Y"));
		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		for (int y = 0; y < year.length; y++) {
			for (int i = 0; i < ids.length; i++) {
				TennisData TData = new TennisData(ids[i], year[y], "WTA", true);
				HashMap<String, Double> misfortune_map = TData.getMisfortune_map();
				Map<String, Double> misfortune_sorted = sortByValue(misfortune_map);
				int[] misfortune_count = TData.getMisfortune_count();
				ArrayList<Player> players = TData.getPlayers();
				System.out.println("Season of " + year[y]);
				csvPrinterH.printRecord("Slams " + year[y] + " " + "WTA", "---------", " ", " ", " ", " ");
				int tcount = 0;
				for (int j = 0; j < misfortune_count.length; j++) {
					csvPrinterH.printRecord("Misfortune of " + j, misfortune_count[j], " ", " ", " ", " ", " ");
					tcount +=misfortune_count[j];
				}
				csvPrinterH.printRecord("Total misfortunates", tcount, " ", " ", " ", " ", " ");
				Double inter = (TData.getCommon_player_count()[3]*100.0)/128.0;
				csvPrinterH.printRecord("Intersection of players", TData.getCommon_player_count()[3] +" ("+df.format(inter)+")", " ", " ", " ", " ", " ");
			
				for (Map.Entry<String, Double> e : misfortune_sorted.entrySet()) {
					if (e.getValue() > 2) {
						int current_misfortunate = Integer.parseInt(e.getKey());
						csvPrinterH.printRecord("Misfortune of " + e.getValue(),
								players.get(current_misfortunate).getName(), " ", " ", " ", "");
						int current = Integer.parseInt(year[y]);
						int previous = current - 1;
						int next = current + 2;
						Boolean skip = false;
						if (players.get(current_misfortunate).getName().equals("Taylor Harry Fritz"))
							skip = true;
						if (!skip) {
							String fname = players.get(current_misfortunate).getName();
							System.out.println("\t\t" + fname + " with " + e.getValue());
							fname = current + "-" + fname.replaceAll(" ", "");
							csvPrinterH.printRecord(" ", " ", "Rank in Jan",
									TData.getRank(players.get(current_misfortunate).getId(), previous),
									TData.getRank(players.get(current_misfortunate).getId(), current),
									TData.getRank(players.get(current_misfortunate).getId(), next));
							int yb = previous - TData.getYearBirth(players.get(current_misfortunate).getId());
							csvPrinterH.printRecord(" ", " ", "Age", yb, yb + 1, yb + 2);
						}
					}
				}

			}
			csvPrinterH.flush();
		}

		csvPrinterH.close();
		writerH.close();

	}

}
