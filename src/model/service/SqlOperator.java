package model.service;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Optional;

import model.Boat;
import model.BoatType;
import model.Member;

public class SqlOperator {
	private static Connection connection;
	private static final String user = "root";
	private static final String password = "R1!-You_logged_in";
	private static final String databseSchema = "BoatClub";
	private static final String databaseURL = "jdbc:mysql://localhost:3306/" + databseSchema
			+ "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC";

	public static Connection getConnection() throws SQLException {
		if (connection == null) {
			connection = DriverManager.getConnection(databaseURL, user, password);
		}
		return connection;
	}

	public ArrayList<Member> getMembers() {
		try {
			Connection myConnection = getConnection();
			String query = "SELECT * FROM memberBoats";
			// String query2 = "SELECT * FROM boat";
			Statement statement = myConnection.createStatement();
			ResultSet results = statement.executeQuery(query);
			ArrayList<Member> members = new ArrayList<Member>();
			while (results.next()) {
				int memberId = results.getInt("id");
				Optional<Member> member = members.stream().filter(m -> m.getId() == memberId).findFirst();
				if (!member.isPresent()) {
					Member memberNew = new Member(results.getString("Name"), results.getString("personNum"),
							results.getInt("id"));
					members.add(memberNew);
					member = Optional.of(memberNew); // Create a new member if it does not exist
				}
				if (results.getInt("boatid") > 0) {
					Boat boat = new Boat(BoatType.valueOf(results.getString("type")), results.getDouble("length"),
							results.getInt("memberId"));
					boat.setId(results.getInt("boatid"));
					member.get().getBoatList().add(boat);
				}
			}

			return members;
		} catch (SQLException i) {
			System.err.println(i.getMessage());
			return null;
		}
	}

	public void saveMember(Member member) {
		if (member == null) {
			return;
		}
		try {
			Connection myConnection = getConnection();
			String query = "INSERT INTO member(`personNum`,`Name`) VALUES(?,?)";
			if (member.getId() > 0) {
				query = "UPDATE member SET `personNum`= ?, `Name` = ? WHERE id=" + String.valueOf(member.getId());
			}
			PreparedStatement statement = myConnection.prepareStatement(query);
			statement.setString(1, member.getPersonNum());
			statement.setString(2, member.getName());
			boolean sucess = statement.execute();
			if (sucess) {
				if (member.getId() < 0) {
					ResultSet set = statement.getGeneratedKeys();
					set.next();
					member.setId(set.getInt(1));
				}

			}
			for (Boat boat : member.getBoatList()) {
				boat.setMember(member);
				saveBoat(boat);
			}
		} catch (SQLException i) {
			System.err.println(i.getMessage());
		}

	}

	public void deleteMember(Member member) {
		if (member == null || member.getId() == 0) {
			return;
		}
		try {
			Connection myConnection = getConnection();
			String query = "DELETE FROM member WHERE id = ?";

			PreparedStatement statement = myConnection.prepareStatement(query);
			statement.setInt(1, member.getId());
			statement.execute();
		} catch (SQLException i) {
			System.err.println(i.getMessage());
		}

	}

	public void saveBoat(Boat boat) {
		try {
			Connection myConnection = getConnection();
			String query = "INSERT INTO boat(`length`,`type`,`memberId`) VALUES(?,?,?)";
			if (boat.getId() > 0) {
				query = "UPDATE boat SET `length`= ?, `type` = ?, `memberId` = ? WHERE id="
						+ String.valueOf(boat.getId());
			}
			PreparedStatement statement = myConnection.prepareStatement(query);
			statement.setDouble(1, boat.getLength());
			statement.setString(2, boat.getType().toString());
			statement.setInt(3, boat.getMemberId());
			boolean sucess = statement.execute();
			if (sucess) {
				ResultSet set = statement.getGeneratedKeys();
				set.next();
				boat.setId(set.getInt(1));
			}
		} catch (SQLException i) {
			System.err.println(i.getMessage());
		}
	}

	public void deleteBoat(Boat boat) {
		try {
			Connection myConnection = getConnection();
			String query = "DELETE FROM boat WHERE id = ?";
			if (boat.getId() == 0) {
				return;
			}
			PreparedStatement statement = myConnection.prepareStatement(query);
			statement.setInt(1, boat.getId());
			statement.execute();
		} catch (SQLException i) {
			System.err.println(i.getMessage());
		}

	}

	public static boolean login(String userName, String password) {
		try {
			Connection myConnection = getConnection();
			String query = "SELECT displayname FROM staff WHERE name = ? and password=?";
			PreparedStatement statement = myConnection.prepareStatement(query, ResultSet.TYPE_SCROLL_INSENSITIVE,
					ResultSet.CONCUR_READ_ONLY);

			statement.setString(1, userName);

			MessageDigest MD = MessageDigest.getInstance("SHA-1");

			byte[] digest = MD.digest(password.getBytes());
			BigInteger bigNum = new BigInteger(1, digest);

			statement.setString(2, bigNum.toString(16).toLowerCase());
			ResultSet results = statement.executeQuery();
			results.last();
			System.out.println(results.getRow());
			return results.getRow() == 1;

		} catch (SQLException i) {
			System.err.println(i.getMessage());
		} catch (NoSuchAlgorithmException e) {

			e.printStackTrace();
		}
		return false;
	}

	public double getRatio() {
		try {
			Connection myConnection = getConnection();
			String query = "SELECT SUM(members)/SUM(boats) AS ratio FROM ((SELECT 1 AS 'members', 0 AS 'boats' FROM member) UNION ALL (SELECT 0 AS 'members', 1 AS 'boats' FROM boat )) ratio";

			PreparedStatement statement = myConnection.prepareStatement(query);

			boolean sucess = statement.execute();
			if (sucess) {
				ResultSet set = statement.getResultSet();
				set.next();
				return set.getDouble("ratio");
			}
		} catch (SQLException i) {
			System.err.println(i.getMessage());

		}
		return 0;
	}
	
	public int[] getMaxAndMin() {
		try {
			Connection myConnection = getConnection();
			String query = "SELECT id, SUM(COUNT) AS 'COUNT' FROM ((SELECT member.*, 1 AS 'COUNT' FROM member INNER JOIN boat ON member.id = boat.memberid) UNION ALL (SELECT member.*, 0 AS 'COUNT' FROM member LEFT JOIN boat ON member.id = boat.memberid)) A GROUP BY id";

			PreparedStatement statement = myConnection.prepareStatement(query);

			boolean sucess = statement.execute();
			if (sucess) {
				ResultSet set = statement.getResultSet();
				int max = -1;
				int min = 10000;
				while(set.next()) {
					int count = set.getInt("COUNT");
					if(count > max) {
						max = count;
					}
					if (count < min) {
						min = count;
					}
				}
				return new int[] {max, min};
			}
		} catch (SQLException i) {
			System.err.println(i.getMessage());

		}
		return null;
	}
	
	public boolean ValidatePNum(String pNum) {
	
		try {
			Connection myConnection = getConnection();
			String query = "SELECT * FROM member WHERE personNum = ?";

			PreparedStatement statement = myConnection.prepareStatement(query);
			statement.setString(1, pNum);
			if(statement.execute()) {
				ResultSet results = statement.getResultSet();
				return results.getFetchSize() == 0;
			}

		} catch (SQLException i) {
			System.err.println(i.getMessage());
		}
		return false;

	}
}
