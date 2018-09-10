import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class TennisData {
	private int n;
	private String TournamentIdentifier;
	private String DB_Type = "";
	private int[] SeedSlots = { 1, 8, 9, 16, 17, 24, 25, 32, 33, 40, 41, 48, 49, 56, 57, 64, 65, 72, 73, 80, 81, 88, 89,
			96, 97, 104, 105, 112, 113, 121, 122, 128 };
	private ArrayList<Player> players = new ArrayList<Player>();

	private Double[][] h = null;
	private String[][] h_desc = null;
	Double[][] P = null;
	private Connection conn;
	private int seeds = 0;
	private int tournament_size = 0;
	private int tournament_year = 0;
	private int tournament_date = 0;
	private String Path = "";
	private String torunament_name = "";
	private String[] tourney_ids = new String[4];
	private HashMap<String, Double> misfortune_map = new HashMap<String, Double>();
	private HashMap<String, Double> common_player = new HashMap<String, Double>();

	private int[] misfortune_count = new int[5];
	private ArrayList<Integer> unlucky = new ArrayList<Integer>();
	private int twoyears;
	private int oneyear;

	public TennisData(String Tournamentid, String Tournamentyear, String Type, Boolean season)
			throws ParseException, SQLException {
		if (Type.equals("ATP"))
			setDB_Type("ATP");
		else
			setDB_Type("WTA");
		setTournamentIdentifier(Tournamentyear + "-" + Tournamentid);
		setTournament_year(Integer.parseInt(Tournamentyear));
		setConn(DriverManager
				.getConnection("jdbc:sqlite:res/tennis_" + getDB_Type().toLowerCase() + "/" + getDB_Type() + ".db"));
		System.out.println("Populating data structures.This can take a while...");
		if (!season) {
			if (populate())
				System.out.println("\tMain data structures populated.");
		} else {
			if (populate_Season())
				System.out.println("\tMain data structures populated for the Season.");
		}

	}

	private Boolean populate() throws SQLException, ParseException {
		Statement statement = conn.createStatement();
		int seedpos = 0;
		ResultSet res = statement.executeQuery(
				"SELECT m.tourney_name, m.tourney_date,m.round, m.tourney_id, m.match_num, m.draw_size, m.winner_name, m.winner_entry, m.winner_id, p1.country as winner_country, p1.hand as winner_hand, m.winner_seed, m.winner_rank, \n"
						+ "			m.loser_name, m.loser_entry, m.loser_id, p2.country as loser_country, p2.hand as loser_hand, m.loser_seed, m.loser_rank \n"
						+ "FROM matches AS m\n" + "JOIN player p1 ON p1.id = m.winner_id\n"
						+ "JOIN player p2 ON p2.id = m.loser_id\n" + "WHERE 	tourney_id='"
						+ getTournamentIdentifier() + "'  AND round='R128' ORDER BY match_num\n");
		while (res.next()) {
			if (seeds == 0) {
				setTournament_size(res.getInt("draw_size"));
				setTournament_date(res.getInt("tourney_date"));
				setTorunament_name(res.getString("tourney_name"));
				n = getTournament_size();
				h = new Double[n][n];
				P = new Double[n][n];
				h_desc = new String[n][n];
				for (int i = 0; i < n; i++) {
					for (int j = 0; i < n; i++) {
						h_desc[i][j] = "";
						h[i][j] = 0.0;
						P[i][j] = 0.0;
					}
				}
				Path = "in/" + getTournament_year() + "-" + getDB_Type() + "_" + getTorunament_name() + "/";
				File f = new File("in/" + getTournament_year() + "-" + getDB_Type() + "_" + getTorunament_name() + "/");
				f.mkdirs();
				Statement other_s = conn.createStatement();
				ResultSet res_other = other_s.executeQuery(
						"SELECT DISTINCT tourney_id FROM matches WHERE tourney_date<(SELECT tourney_date FROM matches WHERE tourney_id='"
								+ getTournamentIdentifier()
								+ "') AND tourney_level='G' ORDER BY tourney_date DESC LIMIT 4;");
				int counter = 0;
				while (res_other.next()) {
					tourney_ids[counter] = res_other.getString("tourney_id");

					Statement current_t = conn.createStatement();
					ResultSet current_res = current_t
							.executeQuery("SELECT DISTINCT winner_name,loser_name FROM matches WHERE  tourney_id='"
									+ tourney_ids[counter] + "' AND round='R128' ORDER BY match_num;");
					while (current_res.next()) {
						String current_loser = current_res.getString("loser_name");
						if (common_player.containsKey(current_loser)) {
							common_player.replace(current_loser, common_player.get(current_loser) + 1.0);
						} else
							common_player.put(current_loser, 1.0);
						String current_winner = current_res.getString("winner_name");
						if (common_player.containsKey(current_winner))
							common_player.replace(current_winner, common_player.get(current_winner) + 1.0);
						else
							common_player.put(current_winner, 1.0);
					}

					counter++;
				}
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				Calendar c = Calendar.getInstance();
				c.setTime(sdf.parse("" + getTournament_date()));
				c.add(Calendar.DATE, -365);
				oneyear = Integer.valueOf(sdf.format(c.getTime()));
				c.setTime(sdf.parse("" + getTournament_date()));
				c.add(Calendar.DATE, -730);
				twoyears = Integer.valueOf(sdf.format(c.getTime()));
			}
			seedpos = 0;
			if (res.getInt("winner_seed") > 0) {
				seedpos = SeedSlots[seeds];
				seeds++;
			}
			if (res.getInt("loser_seed") > 0) {
				seedpos = SeedSlots[seeds];
				seeds++;
			}
			players.add(new Player(res.getInt("winner_id"), res.getString("winner_name"), res.getInt("winner_seed"),
					seedpos, res.getInt("winner_rank"), res.getString("winner_country"), res.getString("winner_entry"),
					res.getInt("match_num")));
			players.add(new Player(res.getInt("loser_id"), res.getString("loser_name"), res.getInt("loser_seed"),
					seedpos, res.getInt("loser_rank"), res.getString("loser_country"), res.getString("loser_entry"),
					res.getInt("match_num")));

			Statement cou = conn.createStatement();
			ResultSet count = cou.executeQuery("SELECT (SELECT COUNT(*) as count FROM matches WHERE (tourney_id='"
					+ tourney_ids[0] + "'" + "OR tourney_id='" + tourney_ids[1] + "' OR tourney_id='" + tourney_ids[2]
					+ "' OR tourney_id='" + tourney_ids[3] + "') \n"
					+ "	AND	((winner_seed>0 and loser_seed='' AND loser_id='" + res.getInt("winner_id")
					+ "') OR (loser_seed>0 and winner_seed='' and winner_id='" + res.getInt("winner_id") + "'  ))\n"
					+ "				AND round='R128') as count1,"
					+ "(SELECT COUNT(*) as count FROM matches WHERE (tourney_id='" + tourney_ids[0]
					+ "' OR tourney_id='" + tourney_ids[1] + "' OR tourney_id='" + tourney_ids[2] + "' OR tourney_id='"
					+ tourney_ids[3] + "')  and \n" + "				((winner_seed>0 and loser_seed='' AND loser_id='"
					+ res.getInt("loser_id") + "') OR (loser_seed>0 and winner_seed='' and winner_id='"
					+ res.getInt("loser_id") + "'  ))\n" + "				AND round='R128') as count2");

			if (count.next()) {
				if (!(res.getInt("winner_seed") > 0)) {
					if (common_player.containsKey(res.getString("loser_name"))) {
						if (common_player.get(res.getString("loser_name")) > 2.0) {
							misfortune_count[count.getInt("count1")]++;
							misfortune_map.put("" + (players.size() - 2), (double) count.getInt("count1"));
						}
					}
				}
				if (!(res.getInt("loser_seed") > 0)) {
					if (common_player.containsKey(res.getString("winner_name"))) {
						if (common_player.get(res.getString("winner_name")) > 2.0) {
							misfortune_count[count.getInt("count2")]++;
							misfortune_map.put("" + (players.size() - 1), (double) count.getInt("count2"));
						}
					}
				}
			}
			/*
			 * int current_tid =
			 * Arrays.asList(tourney_ids).indexOf(getTournamentIdentifier()); int next_tid =
			 * Arrays.asList(tourney_ids).indexOf(getTournamentIdentifier()) + 1; if
			 * (current_tid == 3) next_tid = 0; String next_tournament =
			 * tourney_ids[next_tid]; Statement check = conn.createStatement(); int
			 * unlucky_id = -1; if (res.getInt("winner_seed") > 0 &&
			 * !(res.getInt("loser_seed") > 0)) unlucky_id = players.size() - 1; else if
			 * (res.getInt("loser_seed") > 0 && !(res.getInt("winner_seed") > 0)) unlucky_id
			 * = players.size() - 2; if (unlucky_id != -1 && !unlucky.contains(unlucky_id))
			 * { ResultSet check_res = check.executeQuery(
			 * "SELECT winner_name,round,loser_name,tourney_name,winner_seed,loser_seed FROM matches WHERE tourney_id='"
			 * + next_tournament + "' AND round='R128' AND (" + "(loser_id='" + unlucky_id +
			 * "' AND winner_seed!='') OR" + "(winner_id='" + unlucky_id +
			 * "' AND loser_seed!=''));"); while (check_res.next()) {
			 * unlucky.add(unlucky_id); } }
			 */
		}
		return true;
	}

	private Boolean populate_Season() throws SQLException, ParseException {
		Statement statement = conn.createStatement();
		int seedpos = 0;
		ResultSet res = statement.executeQuery(
				"SELECT m.tourney_name, m.tourney_date,m.round, m.tourney_id, m.match_num, m.draw_size, m.winner_name, m.winner_entry, m.winner_id, p1.country as winner_country, p1.hand as winner_hand, m.winner_seed, m.winner_rank, \n"
						+ "			m.loser_name, m.loser_entry, m.loser_id, p2.country as loser_country, p2.hand as loser_hand, m.loser_seed, m.loser_rank \n"
						+ "FROM matches AS m\n" + "JOIN player p1 ON p1.id = m.winner_id\n"
						+ "JOIN player p2 ON p2.id = m.loser_id\n" + "WHERE 	tourney_id='"
						+ getTournamentIdentifier() + "'  AND round='R128' ORDER BY match_num\n");
		while (res.next()) {
			if (seeds == 0) {
				setTournament_size(res.getInt("draw_size"));
				setTournament_date(res.getInt("tourney_date"));
				setTorunament_name(res.getString("tourney_name"));
				n = getTournament_size();
				h = new Double[n][n];
				P = new Double[n][n];
				h_desc = new String[n][n];
				for (int i = 0; i < n; i++) {
					for (int j = 0; i < n; i++) {
						h_desc[i][j] = "";
						h[i][j] = 0.0;
						P[i][j] = 0.0;
					}
				}
				Path = "in/" + getTournament_year() + "-" + getDB_Type() + "_" + getTorunament_name() + "/";
				File f = new File("in/" + getTournament_year() + "-" + getDB_Type() + "_" + getTorunament_name() + "/");
				f.mkdirs();
				tourney_ids[0] = getTournament_year() + "-520";
				tourney_ids[1] = getTournament_year() + "-540";
				tourney_ids[2] = getTournament_year() + "-560";
				tourney_ids[3] = getTournament_year() + "-580";
				for (int i = 0; i < 4; i++) {
					Statement current_t = conn.createStatement();
					ResultSet current_res = current_t
							.executeQuery("SELECT DISTINCT winner_name,loser_name FROM matches WHERE  tourney_id='"
									+ tourney_ids[i] + "' AND round='R128' ORDER BY match_num;");
					while (current_res.next()) {
						String current_loser = current_res.getString("loser_name");
						if (common_player.containsKey(current_loser)) {
							common_player.replace(current_loser, common_player.get(current_loser) + 1.0);
						} else
							common_player.put(current_loser, 1.0);
						String current_winner = current_res.getString("winner_name");
						if (common_player.containsKey(current_winner))
							common_player.replace(current_winner, common_player.get(current_winner) + 1.0);
						else
							common_player.put(current_winner, 1.0);
					}
				}
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				Calendar c = Calendar.getInstance();
				c.setTime(sdf.parse("" + getTournament_date()));
				c.add(Calendar.DATE, -365);
				oneyear = Integer.valueOf(sdf.format(c.getTime()));
				c.setTime(sdf.parse("" + getTournament_date()));
				c.add(Calendar.DATE, -730);
				twoyears = Integer.valueOf(sdf.format(c.getTime()));
			}
			seedpos = 0;
			if (res.getInt("winner_seed") > 0) {
				seedpos = SeedSlots[seeds];
				seeds++;
			}
			if (res.getInt("loser_seed") > 0) {
				seedpos = SeedSlots[seeds];
				seeds++;
			}
			players.add(new Player(res.getInt("winner_id"), res.getString("winner_name"), res.getInt("winner_seed"),
					seedpos, res.getInt("winner_rank"), res.getString("winner_country"), res.getString("winner_entry"),
					res.getInt("match_num")));
			players.add(new Player(res.getInt("loser_id"), res.getString("loser_name"), res.getInt("loser_seed"),
					seedpos, res.getInt("loser_rank"), res.getString("loser_country"), res.getString("loser_entry"),
					res.getInt("match_num")));

			Statement cou = conn.createStatement();
			ResultSet count = cou.executeQuery("SELECT (SELECT COUNT(*) as count FROM matches WHERE (tourney_id='"
					+ tourney_ids[0] + "'" + "OR tourney_id='" + tourney_ids[1] + "' OR tourney_id='" + tourney_ids[2]
					+ "' OR tourney_id='" + tourney_ids[3] + "') \n"
					+ "	AND	((winner_seed>0 and loser_seed='' AND loser_id='" + res.getInt("winner_id")
					+ "') OR (loser_seed>0 and winner_seed='' and winner_id='" + res.getInt("winner_id") + "'  ))\n"
					+ "				AND round='R128') as count1,"
					+ "(SELECT COUNT(*) as count FROM matches WHERE (tourney_id='" + tourney_ids[0]
					+ "' OR tourney_id='" + tourney_ids[1] + "' OR tourney_id='" + tourney_ids[2] + "' OR tourney_id='"
					+ tourney_ids[3] + "')  and \n" + "				((winner_seed>0 and loser_seed='' AND loser_id='"
					+ res.getInt("loser_id") + "') OR (loser_seed>0 and winner_seed='' and winner_id='"
					+ res.getInt("loser_id") + "'  ))\n" + "				AND round='R128') as count2");

			if (count.next()) {
				if (!(res.getInt("winner_seed") > 0)) {
					if (common_player.containsKey(res.getString("loser_name"))) {
						if (common_player.get(res.getString("loser_name")) > 2.0) {
							misfortune_count[count.getInt("count1")]++;
							misfortune_map.put("" + (players.size() - 2), (double) count.getInt("count1"));
						}
					}
				}
				if (!(res.getInt("loser_seed") > 0)) {
					if (common_player.containsKey(res.getString("winner_name"))) {
						if (common_player.get(res.getString("winner_name")) > 2.0) {
							misfortune_count[count.getInt("count2")]++;
							misfortune_map.put("" + (players.size() - 1), (double) count.getInt("count2"));
						}
					}
				}
			}
			/*
			 * int current_tid =
			 * Arrays.asList(tourney_ids).indexOf(getTournamentIdentifier()); int next_tid =
			 * Arrays.asList(tourney_ids).indexOf(getTournamentIdentifier()) + 1; if
			 * (current_tid == 3) next_tid = 0; String next_tournament =
			 * tourney_ids[next_tid]; Statement check = conn.createStatement(); int
			 * unlucky_id = -1; if (res.getInt("winner_seed") > 0 &&
			 * !(res.getInt("loser_seed") > 0)) unlucky_id = players.size() - 1; else if
			 * (res.getInt("loser_seed") > 0 && !(res.getInt("winner_seed") > 0)) unlucky_id
			 * = players.size() - 2; if (unlucky_id != -1 && !unlucky.contains(unlucky_id))
			 * { ResultSet check_res = check.executeQuery(
			 * "SELECT winner_name,round,loser_name,tourney_name,winner_seed,loser_seed FROM matches WHERE tourney_id='"
			 * + next_tournament + "' AND round='R128' AND (" + "(loser_id='" + unlucky_id +
			 * "' AND winner_seed!='') OR" + "(winner_id='" + unlucky_id +
			 * "' AND loser_seed!=''));"); while (check_res.next()) {
			 * unlucky.add(unlucky_id); } }
			 */
		}
		return true;
	}

	private Double[][] generate_P() throws ErrorThrower, SQLException, IOException {
		System.out.println("Generating P");
		for (int player_a = 0; player_a < n; player_a++) {
			System.out.println("\tEvaulting p-s for " + players.get(player_a).getName());
			for (int player_b = 0; player_b < n && player_b < player_a; player_b++) {

				Double P_rank = 0.0;
				Double P_H2H = -1.0;
				Double P_win = 0.0;
				Statement stat1 = conn.createStatement();
				ResultSet res = stat1.executeQuery("SELECT (SELECT COUNT(*) as count FROM matches\n"
						+ "where winner_name = '" + players.get(player_a).getName() + "' AND loser_name='"
						+ players.get(player_b).getName() + "'\n" + "				AND tourney_date < "
						+ getTournament_date() + " AND tourney_date > " + twoyears + "  AND tourney_id!='"
						+ TournamentIdentifier + "') as win," + "(SELECT COUNT(*) as count FROM matches\n"
						+ "where loser_name = '" + players.get(player_a).getName() + "' AND winner_name='"
						+ players.get(player_b).getName() + "'\n" + "				AND tourney_date < "
						+ getTournament_date() + " AND tourney_date > " + twoyears + "  AND tourney_id!='"
						+ TournamentIdentifier + "') as lose");
				Double wins = res.getDouble("win");
				Double loses = res.getDouble("lose");
				if (players.get(player_a).getRanking() > players.get(player_b).getRanking())
					P_rank = 1.0;
				else
					P_rank = 0.0;

				if ((wins + loses) > 0) {
					// H2H Exists!
					P_H2H = (wins / (wins + loses));
					P_win = 0.65 * P_rank + 0.35 * P_H2H;
					// System.out.println("wins:"+ wins+" | loses:"+loses + "| ph2h:"+P_H2H + "|
					// prank:"+P_rank);
					// System.out.println("P between "+players.get(player_a).getName() +"
					// "+players.get(player_b).getName()+"="+P_win);
				} else {
					// No H2H
					P_H2H = 0.0;
					P_win = P_rank;
				}

				P[player_a][player_b] = P_win;
				P[player_b][player_a] = 1 - P_win;
				P[player_a][player_a] = 0.0;
			}
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(Path + "P.txt"));
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (j != n) {
					bw.write(P[i][j] + "\t");
				} else {
					bw.write("" + P[i][j]);
				}
			}
			bw.write("\n");

		}
		bw.close();
		return (P);
	}

	private void generate_H() throws SQLException, ErrorThrower, IOException {
		Statement statement = conn.createStatement();
		Double val = 0.0;
		Double val_tmp = 0.0;
		String desc = "";
		System.out.println("Generating H");
		for (int i = 0; i < n; i++) {
			System.out.println("\tEvaulting h-s for " + players.get(i).getName());
			for (int j = 0; j < n; j++) {
				if (i < j) {
					desc = "";
					val = 0.0;
					val_tmp = 0.0;
					// Set to Q and LL to zero
					// Generate a description for encountered conflicts in a separate files
					// Penalty sum up: Country & 1ST: +5; 2nd: +2; QF & SF: +0.5;
					if (players.get(i).isnotQualified() && players.get(j).isnotQualified()) {
						if (players.get(i).getCountry().equals(players.get(j).getCountry())) {
							val += 5.0;
							desc += "C";
						}

						// Conflict measures are assigned depending on the round
						ResultSet res = statement.executeQuery("SELECT (SELECT COUNT(*) FROM matches where\n"
								+ "								( (winner_name = '" + players.get(i).getName()
								+ "' AND loser_name='" + players.get(j).getName() + "') OR(loser_name = '"
								+ players.get(i).getName() + "'\n" + "								 AND winner_name='"
								+ players.get(j).getName() + "'))\n"
								+ "								 AND ((draw_size='128' AND round='R128') OR (draw_size='64' AND round='R64') OR\n"
								+ "								 (draw_size='32' AND round='R32')) AND tourney_id !='"
								+ TournamentIdentifier + "' \n" + "								 AND tourney_date < "
								+ getTournament_date() + " AND tourney_date > " + oneyear + ") as num_first,\n"
								+ "(SELECT COUNT(*) AS num_second FROM matches where\n"
								+ "								( (winner_name = '" + players.get(i).getName()
								+ "' AND loser_name='" + players.get(j).getName() + "') OR(loser_name = '"
								+ players.get(i).getName() + "'\n" + "								 AND winner_name='"
								+ players.get(j).getName() + "'))\n"
								+ "								 AND ((draw_size='128' AND round='R64') OR (draw_size='64' AND round='R32') OR\n"
								+ "								 (draw_size='32' AND round='R16')) AND tourney_id !='"
								+ TournamentIdentifier + "' \n" + "								 AND tourney_date < "
								+ getTournament_date() + " AND tourney_date > " + oneyear + ") as num_second,\n"
								+ "(SELECT COUNT(*) AS num_second FROM matches where\n"
								+ "								( (winner_name = '" + players.get(i).getName()
								+ "' AND loser_name='" + players.get(j).getName() + "') OR(loser_name = '"
								+ players.get(i).getName() + "'\n" + "								 AND winner_name='"
								+ players.get(j).getName() + "'))\n"
								+ "								 AND ((draw_size='128' AND round='R32') OR (draw_size='64' AND round='R16')) AND tourney_id !='"
								+ TournamentIdentifier + "' \n" + "								 AND tourney_date < "
								+ getTournament_date() + " AND tourney_date > " + oneyear + ") as num_third,\n"
								+ "(SELECT COUNT(*) AS num_second FROM matches where\n"
								+ "								( (winner_name = '" + players.get(i).getName()
								+ "' AND loser_name='" + players.get(j).getName() + "') OR(loser_name = '"
								+ players.get(i).getName() + "'\n" + "								 AND winner_name='"
								+ players.get(j).getName() + "'))\n"
								+ "								 AND ((draw_size='128' AND (round='QF' OR round='SF')) OR (draw_size='64' AND (round='QF' OR round='SF')) OR\n"
								+ "								 (draw_size='32' AND (round='QF' OR round='SF'))) AND tourney_id !='"
								+ TournamentIdentifier + "' \n" + "								 AND tourney_date < "
								+ getTournament_date() + " AND tourney_date > " + oneyear + ") as num_last;");

						if (res.getInt("num_first") > 0) {
							desc += "1";
							val_tmp += 5.0 * res.getInt("num_first");
						}
						if (res.getInt("num_second") > 0) {
							desc += "2";
							val_tmp += 2.0 * res.getInt("num_second");
						}
						if (res.getInt("num_third") > 0) {
							desc += "3";
							val_tmp += 1.0 * res.getInt("num_third");
						}
						if (res.getInt("num_last") > 0) {
							desc += "L";
							val_tmp += 0.5 * res.getInt("num_last");
						}
					}
					h[i][j] = val + val_tmp;
					h[j][i] = h[i][j];
					if (desc.equals("")) {
						desc = "-";
					}
					h_desc[i][j] = desc;
					h_desc[j][i] = h_desc[i][j];

				}
				if (i == j) {
					h[i][j] = 0.0;
					h_desc[i][j] = "-";
				}
			}
		}
		BufferedWriter bw = new BufferedWriter(new FileWriter(Path + "H.txt"));
		BufferedWriter bw_desc = new BufferedWriter(new FileWriter(Path + "H_desc.txt"));
		BufferedWriter pw = new BufferedWriter(new FileWriter(Path + "H_players.txt"));
		for (int i = 0; i < n; i++) {
			pw.write(players.get(i).getName() + "\n");
			for (int j = 0; j < n; j++) {
				if (j != n) {
					bw.write(h[i][j] + "\t");
					bw_desc.write(h_desc[i][j] + "\t");
				} else {
					bw.write("" + h[i][j]);
					bw_desc.write("" + h_desc[i][j]);
				}
			}
			bw.write("\n");
			bw_desc.write("\n");

		}
		bw.close();
		bw_desc.close();
		pw.close();
	}

	public Double[][] loadP() throws ErrorThrower, SQLException, IOException {
		File f = new File(Path + "P.txt");
		if (f.exists() && !f.isDirectory()) {
			return readP();
		} else {
			generate_P();
			return readP();
		}
	}

	public Double[][] loadH() throws ErrorThrower, SQLException, IOException {
		File f = new File(Path + "H.txt");
		if (f.exists() && !f.isDirectory()) {
			return readH();
		} else {
			generate_H();
			return readH();
		}
	}

	public String[][] loadH_desc() throws ErrorThrower, SQLException, IOException {
		File f = new File(Path + "H_desc.txt");
		if (f.exists() && !f.isDirectory()) {
			return readH_desc();
		} else {
			generate_H();
			return readH_desc();
		}
	}

	private Double[][] readP() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File(Path + "P.txt"));
			for (int i = 0; i < n && input.hasNextLine(); i++) {
				for (int j = 0; j < n; j++) {
					P[i][j] = input.nextDouble();
				}
				input.nextLine();
			}

		} catch (IOException e1) {
			throw new ErrorThrower("IO -" + e1.getMessage());
		}
		input.close();
		return P;
	}

	private Double[][] readH() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File(Path + "H.txt"));
			for (int i = 0; i < n && input.hasNextLine(); i++) {
				for (int j = 0; j < n; j++) {
					h[i][j] = input.nextDouble();
				}
				input.nextLine();
			}

		} catch (IOException e1) {
			throw new ErrorThrower("IO -" + e1.getMessage());
		}
		input.close();
		return h;
	}

	public String[][] readH_desc() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File(Path + "H_desc.txt"));
			for (int i = 0; i < n && input.hasNextLine(); i++) {
				for (int j = 0; j < n; j++) {
					h_desc[i][j] = input.next();
				}
				input.nextLine();
			}

		} catch (IOException e1) {
			throw new ErrorThrower("IO -" + e1.getMessage());
		}
		input.close();
		return h_desc;
	}

	public int[][] getRealTournament() throws SQLException {
		int rounds = (int) (Math.log(n) / Math.log(2));
		int[][] ACTUAL_Rounds = new int[n][rounds];
		for (int t = 0; t < (rounds); t++)
			for (int i = 0; i < n; i++)
				ACTUAL_Rounds[i][t] = -1;

		Statement statement = conn.createStatement();
		ResultSet res2 = statement.executeQuery("SELECT winner_name, loser_name\n" + "FROM matches WHERE tourney_id='"
				+ getTournamentIdentifier() + "' ORDER BY match_num;");
		int id_w = 0, id_l = 0, current_round = 0, rindex = 0, nrindex = 0;
		while (res2.next()) {
			id_w = getID(players, res2.getString("winner_name"));
			id_l = getID(players, res2.getString("loser_name"));
			ACTUAL_Rounds[rindex][current_round] = id_w;
			if ((current_round + 1) < rounds)
				ACTUAL_Rounds[nrindex][current_round + 1] = id_w;
			rindex++;
			nrindex++;
			ACTUAL_Rounds[rindex][current_round] = id_l;
			rindex++;
			if ((nrindex) == (int) Math.pow(2, (rounds - current_round - 1))) {
				current_round++;
				rindex = 0;
				nrindex = 0;
			}

		}
		return ACTUAL_Rounds;
	}

	public int getRank(int playerId, int year) throws SQLException {
		Statement statement = conn.createStatement();
		ResultSet res2 = statement.executeQuery("SELECT pos FROM ranking where player_id='" + playerId + "' "
				+ "and date>'" + year + "0000' ORDER BY date LIMIT 1;");
		int pos = -1;
		while (res2.next())
			pos = res2.getInt("pos");

		return pos;
	}

	public int getYearBirth(int playerId) throws SQLException, ParseException {
		Statement statement = conn.createStatement();
		ResultSet res2 = statement.executeQuery("SELECT birth FROM player where id='" + playerId + "' LIMIT 1;");
		int ret = -1;
		while (res2.next())
			ret = Integer.parseInt(res2.getString("birth").substring(0, 4));

		return ret;
	}

	public String getDB_Type() {
		return DB_Type;
	}

	private void setDB_Type(String dB_Type) {
		DB_Type = dB_Type;
	}

	public String getTournamentIdentifier() {
		return TournamentIdentifier;
	}

	private void setTournamentIdentifier(String tournamentIdentifier) {
		TournamentIdentifier = tournamentIdentifier;
	}

	private void setConn(Connection conn) {
		this.conn = conn;
	}

	public int getTournament_size() {
		return tournament_size;
	}

	private void setTournament_size(int tournament_size) {
		this.tournament_size = tournament_size;
	}

	public int getTournament_date() {
		return tournament_date;
	}

	private void setTournament_date(int tournament_date) {
		this.tournament_date = tournament_date;
	}

	public String getTorunament_name() {
		return torunament_name;
	}

	private void setTorunament_name(String torunament_name) {
		this.torunament_name = torunament_name;
	}

	public int getTournament_year() {
		return tournament_year;
	}

	public int getSeeds() {
		return seeds;
	}

	private void setTournament_year(int tournament_year) {
		this.tournament_year = tournament_year;
	}

	public HashMap<String, Double> getMisfortune_map() {
		return misfortune_map;
	}

	public int[] getMisfortune_count() {
		return misfortune_count;
	}

	public static int getID(ArrayList<Player> p, String name) {
		Boolean notfound = true;
		int ret = -1;
		for (int i = 0; i < p.size() && notfound; i++) {
			if (p.get(i).getName().equals(name)) {
				notfound = false;
				ret = i;
			}
		}
		return ret;
	}

	/**
	 * @return the players
	 */
	public ArrayList<Player> getPlayers() {
		return players;
	}

	public int[] getCommon_player_count() {
		int[] cplayer = new int[4];
		for (Map.Entry<String, Double> e : common_player.entrySet()) {
			if (e.getValue() == 1)
				cplayer[0]++;
			if (e.getValue() == 2)
				cplayer[1]++;
			if (e.getValue() == 3)
				cplayer[2]++;
			if (e.getValue() == 4)
				cplayer[3]++;
		}
		return cplayer;
	}

	public ArrayList<Integer> getUnlucky() {

		return unlucky;
	}
}
