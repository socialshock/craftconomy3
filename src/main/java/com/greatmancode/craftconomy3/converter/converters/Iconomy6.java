/*
 * This file is part of Craftconomy3.
 *
 * Copyright (c) 2011-2014, Greatman <http://github.com/greatman/>
 *
 * Craftconomy3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Craftconomy3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Craftconomy3.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.greatmancode.craftconomy3.converter.converters;

import com.greatmancode.craftconomy3.Common;
import com.greatmancode.craftconomy3.converter.Converter;
import com.greatmancode.craftconomy3.database.tables.iconomy.IConomyTable;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Converter for iConomy 6.
 *
 * @author greatman
 */
public class Iconomy6 extends Converter {
    private BufferedReader flatFileReader = null;
    private HikariDataSource db;

    public Iconomy6() {
        getDbTypes().add("flatfile");
        getDbTypes().add("minidb");
        getDbTypes().add("sqlite");
        getDbTypes().add("mysql");
    }

    @Override
    public List<String> getDbInfo() {

        if (getDbInfoList().size() == 0) {
            if (getSelectedDbType().equals("flatfile") || getSelectedDbType().equals("minidb") || getSelectedDbType().equals("sqlite")) {
                getDbInfoList().add("filename");
            } else if (getSelectedDbType().equals("mysql")) {
                getDbInfoList().add("address");
                getDbInfoList().add("port");
                getDbInfoList().add("username");
                getDbInfoList().add("password");
                getDbInfoList().add("database");
            }
        }
        return getDbInfoList();
    }

    @Override
    public boolean connect() {
        boolean result = false;
        if (getSelectedDbType().equals("flatfile") || getSelectedDbType().equals("minidb")) {
            result = loadFile();
        } else if (getSelectedDbType().equals("mysql")) {
            loadMySQL();
        } else if (getSelectedDbType().equals("sqlite")) {
            loadSQLite();
        }

        if (db != null) {
            result = true;
        }
        return result;
    }

    /**
     * Allow to load a flatfile database.
     *
     * @return True if the file is open. Else false.
     */
    private boolean loadFile() {
        boolean result = false;
        File dbFile = new File(Common.getInstance().getServerCaller().getDataFolder(), getDbConnectInfo().get("filename"));
        if (dbFile.exists()) {
            try {
                flatFileReader = new BufferedReader(new FileReader(dbFile));
                result = true;
            } catch (FileNotFoundException e) {
                Common.getInstance().getLogger().severe("iConomy database file not found!");
            }
        }
        return result;
    }

    /**
     * Allow to load a MySQL database.
     */
    private void loadMySQL() {
        try {
            HikariConfig config = new HikariConfig();
            config.setMaximumPoolSize(10);
            config.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
            config.addDataSourceProperty("serverName", getDbConnectInfo().get("address"));
            config.addDataSourceProperty("port", getDbConnectInfo().get("port"));
            config.addDataSourceProperty("databaseName", getDbConnectInfo().get("database"));
            config.addDataSourceProperty("user", getDbConnectInfo().get("username"));
            config.addDataSourceProperty("password", getDbConnectInfo().get("password"));
            config.addDataSourceProperty("autoDeserialize", true);
            db = new HikariDataSource(config);
        } catch (NumberFormatException e) {
            Common.getInstance().getLogger().severe("Illegal Port!");
        }
    }

    /**
     * Allow to load a SQLite database.
     */
    private void loadSQLite() {
        //TODO Hikari this
    }

    @Override
    public boolean importData(String sender) {
        boolean result = false;
        if (flatFileReader != null) {
            result = importFlatFile(sender);
        } else if (db != null) {
            result = importDatabase(sender);
        }
        return result;
    }

    /**
     * Import accounts from a flatfile.
     *
     * @param sender The command sender so we can send back messages.
     * @return True if the convert is done. Else false.
     */
    private boolean importFlatFile(String sender) {
        boolean result = false;

        try {
            List<String> file = new ArrayList<String>();
            String str;
            while ((str = flatFileReader.readLine()) != null) {
                file.add(str);
            }
            flatFileReader.close();
            List<User> userList = new ArrayList<User>();
            for (String aFile : file) {
                String[] info = aFile.split(" ");
                try {
                    double balance = Double.parseDouble(info[1].split(":")[1]);
                    userList.add(new User(info[0], balance));
                } catch (NumberFormatException e) {
                    Common.getInstance().sendConsoleMessage(Level.SEVERE, "User " + info[0] + " have a invalid balance" + info[1]);
                } catch (ArrayIndexOutOfBoundsException e) {
                    Common.getInstance().sendConsoleMessage(Level.WARNING, "Line not formatted correctly. I read:" + Arrays.toString(info));
                }
            }
            addAccountToString(userList);
            addBalance(sender, userList);
            result = true;
        } catch (IOException e) {
            Common.getInstance().getLogger().severe("A error occured while reading the iConomy database file! Message: " + e.getMessage());
        }
        return result;
    }

    /**
     * Import accounts from the database.
     *
     * @param sender The command sender so we can send back messages.
     * @return True if the convert is done. Else false.
     */
    private boolean importDatabase(String sender) {
        try {
            Connection connection = db.getConnection();
            PreparedStatement statement = connection.prepareStatement(IConomyTable.SELECT_ENTRY);
            ResultSet set = statement.executeQuery();
            List<User> userList = new ArrayList<User>();
            while (set.next()) {
                userList.add(new User(set.getString("username"), set.getDouble("balance")));
            }
            statement.close();
            connection.close();
            addAccountToString(userList);
            addBalance(sender, userList);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return true;
    }
}
