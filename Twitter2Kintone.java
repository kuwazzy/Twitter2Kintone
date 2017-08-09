package cdataj;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class Twitter2Kintone{

	public static String UrlKintone = "jdbc:kintone:URL=https://****.cybozu.com;User=****;Password=****;RTK=****;";
	public static String UrlTwitter = "jdbc:twitter:OAuth Access Token Secret=****;OAuth Access Token=****;OAuth Client Id=****;OAuth Client Secret=****;RTK=****;";
	public static String TableNameKintone = "kintonedevcamp";
	public static String HashtagKintone = "#kintone OR #kintonedevcamp";

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
			statt.execute("SELECT ID, CONCAT(FORMAT(Created_At,'yyyy-MM-dd'), 'T', FORMAT(Created_At,'HH:mm:ssZ')) AS Created_At, text, Source, Favorite_Count, Retweet_Count, Retweeted_Status_Id, From_User_Id, From_User_Screen_Name, From_User_Name From_User_Name, From_User_Location, From_User_Profile_URL, From_User_Profile_Image_Url FROM Tweets WHERE SEARCHTERMS = '" + HashtagKintone + "' AND MIN_ID = '" + maxid + "';");
			ResultSet rst=statt.getResultSet();
			String insertstring = "";
			int affectedcount = 0;
			while(rst.next()){
				//Insert into kintone
				String cmd = "INSERT INTO '" + TableNameKintone + "' (ID, Created_At, コメント, Source, Favorite_Count, Retweet_Count, Retweeted_Status_Id, From_User_Id, From_User_Screen_Name, 投稿ユーザー, From_User_Location, From_User_Profile_URL, From_User_Profile_Image_Url) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?);";
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
				affectedcount += pstmt.executeUpdate();
			}
			System.out.println(affectedcount+" rows are affected");
			long end = System.currentTimeMillis();
			System.out.println((end - start)  + "ms");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
