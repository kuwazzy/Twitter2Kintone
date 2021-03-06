package cdataj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;

public class Twitter2Kintone{


	public static String UrlKintone = "jdbc:kintone:URL=https://****.cybozu.com;User=****;Password=****;RTK=****;";
	public static String UrlTwitter = "jdbc:twitter:OAuth Access Token Secret=****;OAuth Access Token=****;OAuth Client Id=****;OAuth Client Secret=****;RTK=****;";
	public static String TableNameKintone = "Twitter2Kintone";
	public static String HashtagKintone = "#qiita";

	public static String[] ListMember = { "" };  // Except From_User_Name

	public static void main(String[] args) {
		try {
			long start = System.currentTimeMillis();

			//Connection for kintone
			Class.forName("cdata.jdbc.kintone.KintoneDriver");
			//System.out.println(cdata.jdbc.kintone.KintoneDriver.getRTK());
			Connection connk = DriverManager.getConnection(UrlKintone);
			Statement statk = connk.createStatement();

			//Connection for Twitter
			Class.forName("cdata.jdbc.twitter.TwitterDriver");
			//System.out.println(cdata.jdbc.twitter.TwitterDriver.getRTK());
			Connection connt = DriverManager.getConnection(UrlTwitter);
			Statement statt = connt.createStatement();

			//Select max Twitter ID from kintone
			statk.execute("SELECT max(ID) AS MAX_ID FROM '" + TableNameKintone + "'");
			ResultSet rsk=statk.getResultSet();
			String maxid = "";
			while(rsk.next()){
				maxid = rsk.getString(1);
			}

			//Select Tweets from Twitter
			statt.execute("SELECT ID, CONCAT(FORMAT(Created_At,'yyyy-MM-dd'), 'T', FORMAT(Created_At,'HH:mm:ssZ')) AS Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name FROM Tweets WHERE SEARCHTERMS = '" + HashtagKintone + "' AND MIN_ID = '" + maxid + "';");
			ResultSet rst=statt.getResultSet();
			String insertstring = "";
			int affectedcount = 0;
			while(rst.next()){
				//Insert into kintone
				String cmd = "INSERT INTO '" + TableNameKintone + "' (ID, Created_At, text, From_User_Id, From_User_Screen_Name, From_User_Name) VALUES (?,?,?,?,?,?);";
				PreparedStatement pstmt = connk.prepareStatement(cmd,Statement.RETURN_GENERATED_KEYS);
				for(int i=1;i<=rst.getMetaData().getColumnCount();i++)
				{
					insertstring = rst.getString(i);
					if (insertstring == null){
						insertstring = "";
					}
					pstmt.setString(i, insertstring);
					System.out.println(rst.getMetaData().getColumnName(i) +"="+insertstring);
				}

				if(Arrays.asList(ListMember).contains(rst.getString(6))){
					//Skip
				}
				else{
					affectedcount += pstmt.executeUpdate();
				}
			}
			System.out.println(affectedcount+" rows are affected");
			long end = System.currentTimeMillis();
			System.out.println((end - start)  + "ms");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
