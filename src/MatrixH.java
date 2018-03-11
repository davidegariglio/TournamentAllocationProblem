import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Scanner;

public class MatrixH {
	private int n;
	private String tourney_id;
	private ArrayList<Player> players = new ArrayList<Player>();
	Double[][] h = null;
	String[][] h_desc = null;
	int date;
	int twoyears;
	int oneyear;

	public MatrixH(int enne, ArrayList<Player> p, String tournamentid, int dateinput) throws ParseException {
		super();
		n = enne;
		players = p;
		tourney_id = tournamentid;
		h = new Double[n][n];
		h_desc = new String[n][n];
		date = dateinput;
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		Calendar c = Calendar.getInstance();
		c.setTime(sdf.parse("" + date));
		c.add(Calendar.DATE, -365);
		oneyear = Integer.valueOf(sdf.format(c.getTime()));
		c.setTime(sdf.parse("" + date));
		c.add(Calendar.DATE, -730);
		twoyears = Integer.valueOf(sdf.format(c.getTime()));
	}

	public Double P_win(int player_a, int player_b) throws ErrorThrower, SQLException {
		Double P_rank = 0.0;
		Double P_H2H = -1.0;
		Double P_win = 0.0;
		Connection conn;
		try {
			conn = DriverManager.getConnection("jdbc:sqlite:atpdatabase.db");
		} catch (SQLException e1) {
			throw new ErrorThrower("Can't connect to SQLite DB: " + e1.getMessage());
		}
		Statement stat1 = conn.createStatement();
		ResultSet res1 = stat1.executeQuery("SELECT COUNT(*) as count FROM matches\n" + "where winner_name = '"
				+ players.get(player_a).getName() + "' AND loser_name='" + players.get(player_b).getName() + "'\n"
				+ "				AND tourney_date < " + date + " AND tourney_date > " + twoyears + "  AND tourney_id!='"
				+ tourney_id + "'");
		Double wins = res1.getDouble("count");
		Statement stat2 = conn.createStatement();
		ResultSet res2 = stat2.executeQuery("SELECT COUNT(*) as count FROM matches\n" + "where loser_name = '"
				+ players.get(player_a).getName() + "' AND winner_name='" + players.get(player_b).getName() + "'\n"
				+ "				AND tourney_date < " + date + " AND tourney_date > " + twoyears + "  AND tourney_id!='"
				+ tourney_id + "'");
		Double loses = res2.getDouble("count");

		if (players.get(player_a).getRanking() > players.get(player_b).getRanking())
			P_rank = 1.0;
		else
			P_rank = 0.0;

		if ((wins + loses) > 0) {
			// H2H Exists!
			P_H2H = (wins / (wins + loses));
			P_win = 0.65 * P_rank + 0.35 * P_H2H;
			//System.out.println("wins:"+ wins+" | loses:"+loses + "| ph2h:"+P_H2H + "| prank:"+P_rank);
			//System.out.println("P between "+players.get(player_a).getName() +" "+players.get(player_b).getName()+"="+P_win);
		} else {
			// No H2H
			P_H2H = 0.0;
			P_win = P_rank;
		}
		conn.close();
		return (P_win);
	}

	public void generate() throws SQLException, ErrorThrower, IOException {
		Connection conn;
		try {
			conn = DriverManager.getConnection("jdbc:sqlite:atpdatabase.db");
		} catch (SQLException e1) {
			throw new ErrorThrower("Can't connect to SQLite DB: " + e1.getMessage());
		}
		Statement statement = conn.createStatement();
		statement.setQueryTimeout(15);
		Double val = 0.0;
		Double val_tmp = 0.0;
		String desc = "";
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i < j) {
					desc = "";
					val = 0.0;
					val_tmp = 0.0;
					// Set to Q and LL to zero
					// Generate a description for encountered conflicts in a separate files
					// Penalty sum up: Country & 1ST: +5; 2nd: +2; QF & SF: +0.5;
					if (players.get(i).isnotQualified() && players.get(j).isnotQualified()) {
						System.out.println("Evaulting "+ players.get(i).getName()+" and "+ players.get(j).getName());
						if (players.get(i).getCountry().equals(players.get(j).getCountry())) {
							val += 5.0;
							desc += "C";
							System.out.println("\t("+desc+"): +"+val);
						}

						// Conflict measures are assigned depending on the round
						// First round
						ResultSet res1 = statement.executeQuery("SELECT COUNT(*) AS num FROM matches where\n"
								+ "( (winner_name = '" + players.get(i).getName() + "' AND loser_name='"
								+ players.get(j).getName() + "')\n" + "OR(loser_name = '" + players.get(i).getName()
								+ "' AND winner_name='" + players.get(j).getName() + "'))\n"
								+ "AND ((draw_size='128' AND round='R128') OR (draw_size='64' AND round='R64') OR"
								+ "(draw_size='32' AND round='R32')) AND tourney_id !='" + tourney_id + "'"
										+ "AND tourney_date < " + date + " AND tourney_date > " + oneyear + ";");
						val_tmp += 5.0 * res1.getInt("num");
						if (res1.getInt("num") > 0) {
							desc += "1";
							System.out.println("\t("+desc+"): +"+5.0 * res1.getInt("num"));
						}

						// Second round
						ResultSet res2 = statement.executeQuery("SELECT COUNT(*) AS num FROM matches where\n"
								+ "( (winner_name = '" + players.get(i).getName() + "' AND loser_name='"
								+ players.get(j).getName() + "')\n" + "OR(loser_name = '" + players.get(i).getName()
								+ "' AND winner_name='" + players.get(j).getName() + "'))\n"
								+ "AND ((draw_size='128' AND round='R64') OR (draw_size='64' AND round='R32') OR"
								+ "(draw_size='32' AND round='R16')) AND tourney_id !='" + tourney_id + "'"
								+ "AND tourney_date < " + date + " AND tourney_date > " + oneyear + ";");
						val_tmp += 2.0 * res2.getInt("num");
						if (res2.getInt("num") > 0) {
							desc += "2";
							System.out.println("\t("+desc+"): +"+2.0 * res2.getInt("num"));
						}

						// Third round
						ResultSet res3 = statement.executeQuery("SELECT COUNT(*) AS num FROM matches where\n"
								+ "( (winner_name = '" + players.get(i).getName() + "' AND loser_name='"
								+ players.get(j).getName() + "')\n" + "OR(loser_name = '" + players.get(i).getName()
								+ "' AND winner_name='" + players.get(j).getName() + "'))\n"
								+ "AND ((draw_size='128' AND round='R32') OR (draw_size='64' AND round='R16'))"
								+ "AND tourney_id!='" + tourney_id + "'"
								+ "AND tourney_date < " + date + " AND tourney_date > " + oneyear + ";");
						val_tmp += res3.getInt("num");
						if (res3.getInt("num") > 0) {
							desc += "3";
							System.out.println("\t("+desc+"): +"+res3.getInt("num"));
						}

						// Quarter-finals and semi-finals
						ResultSet res4 = statement.executeQuery("SELECT COUNT(*) AS num FROM matches where\n"
								+ "( (winner_name = '" + players.get(i).getName() + "' AND loser_name='"
								+ players.get(j).getName() + "')\n" + "OR(loser_name = '" + players.get(i).getName()
								+ "' AND winner_name='" + players.get(j).getName() + "'))\n"
								+ "AND ((draw_size='128' AND (round='QF' OR round='SF')) OR (draw_size='64' AND (round='QF' OR round='SF')) OR"
								+ "(draw_size='32' AND (round='QF' OR round='SF'))) AND tourney_id!='" + tourney_id+"'"
								+ "AND tourney_date < " + date + " AND tourney_date > " + oneyear + ";");
						val_tmp += 0.5 * res4.getInt("num");
						if (res4.getInt("num") > 0) {
							desc += "L";
							System.out.println("\t("+desc+"): +"+0.5 * res4.getInt("num"));
						}
					} else {
						String qual1 = players.get(i).isnotQualified() ? " " : " (" + players.get(i).getEntry() + ") ";
						String qual2 = players.get(j).isnotQualified() ? " " : " (" + players.get(j).getEntry() + ") ";
						System.out.println("Newbie token for " + players.get(i).getName() + qual1 + "and "
								+ players.get(j).getName() + qual2);
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
		BufferedWriter bw = new BufferedWriter(new FileWriter("in/MatrixH_" + tourney_id + ".txt"));
		BufferedWriter bw_desc = new BufferedWriter(
				new FileWriter("in/MatrixH_" + tourney_id + "_desc.txt"));
		BufferedWriter pw = new BufferedWriter(
				new FileWriter("in/MatrixH_" + tourney_id + "_players.txt"));
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
		conn.close();
		System.out.println("MatrixH for tournament " + tourney_id + " generated in in/MatrixH_"
				+ tourney_id + ".txt and  _desc.txt");
	}

	public Double[][] load() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File("in/MatrixH_" + tourney_id + ".txt"));
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
		System.out.println(
				"MatrixH for tournament " + tourney_id + " loaded from in/MatrixH_" + tourney_id + ".txt");
		return h;
	}

	public String[][] load_desc() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File("in/MatrixH_" + tourney_id + "_desc.txt"));
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
		System.out.println("MatrixH_desc for tournament " + tourney_id + " loaded from in/MatrixH_"
				+ tourney_id + ".txt");
		return h_desc;
	}

	public void print() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File("in/MatrixH_" + tourney_id + ".txt"));
			for (int i = 0; i < n && input.hasNextLine(); i++) {
				for (int j = 0; j < n; j++) {
					System.out.print(input.nextDouble() + "\t");
				}
				input.nextLine();
				System.out.println();
			}

		} catch (IOException e1) {
			throw new ErrorThrower("IO -" + e1.getMessage());
		}
		input.close();
	}

	public void print_desc() throws ErrorThrower {
		Scanner input;
		try {
			input = new Scanner(new File("in/MatrixH_" + tourney_id + "_desc.txt"));
			for (int i = 0; i < n && input.hasNextLine(); i++) {
				for (int j = 0; j < n; j++) {
					System.out.print(input.next() + "\t");
				}
				input.nextLine();
				System.out.println();
			}

		} catch (IOException e1) {
			throw new ErrorThrower("IO -" + e1.getMessage());
		}
		input.close();
	}
}
