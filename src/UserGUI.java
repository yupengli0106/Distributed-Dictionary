/**
 * The university of Melbourne
 * COMP90015: Distributed Systems
 * Project 1
 * Author: Yupeng Li
 * Student ID: 1399160
 * Date: 06/04/2023
 */

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.ArrayList;

public class UserGUI extends JFrame{
    private final JTextField wordField;
    private final JTextField meaningField;
    private final Client client;
    public UserGUI(String ip, int port) {
        client = new Client(ip, port);
        if (!client.ConnectServer()) {
            JOptionPane.showMessageDialog(this, "Connect server failed");
            System.exit(-1);
        }
        // set window properties
        setTitle("Dictionary System");
        setSize(500, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // initialize components
        // GUI components
        JLabel wordLabel = new JLabel("Word:", SwingConstants.CENTER);
        JLabel meaningLabel = new JLabel("Meanings:", SwingConstants.CENTER);

        wordField = new JTextField();
        meaningField = new JTextField();

        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        JButton queryButton = new JButton("Query");
        JButton updateButton = new JButton("Update");

        // add components to container
        Container c = getContentPane();
        c.setLayout(new GridLayout(4, 2));

        c.add(wordLabel);
        c.add(meaningLabel);

        c.add(wordField);
        c.add(meaningField);

        c.add(addButton);
        c.add(updateButton);
        c.add(removeButton);
        c.add(queryButton);

        // add action listeners
        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Add();
            }
        });
        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Update();
            }
        });
        removeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Remove();
            }
        });
        queryButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Query();
            }
        });

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                try {
                    client.Close();
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
                e.getWindow().dispose();
            }
        });
        // show window
        setVisible(true);
    }

    public boolean inputCheck(String word){
        if (word.isBlank() || word.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Invalid word，please input again");
            return false;
        }
        if (!word.matches("[a-zA-Z]+")) {
            JOptionPane.showMessageDialog(this, "Invalid word, remove all non-letter characters and try again");
            return false;
        }
        return true;
    }

    public void Add() {
        String word = wordField.getText();
        word = word.trim().toLowerCase();
        if (!inputCheck(word)) {
            return;
        }
        String meanings_s = meaningField.getText();
        if (meanings_s.isEmpty() || meanings_s.isBlank()) {
            JOptionPane.showMessageDialog(this, "Empty meanings，please input again");
            return;
        }

        String[] meanings = meanings_s.split(";");
        ArrayList<String> meanings_list = new ArrayList<>();
        for(String meaning: meanings) {
            meanings_list.add(meaning);
        }
        ErrorCode error_code = ErrorCode.SUCCESS;
        StringBuilder error_context = new StringBuilder("");
        try{
            if(!client.Add(word, meanings_list, error_code, error_context)){
                JOptionPane.showMessageDialog(this, error_context);
                return;
            }
            JOptionPane.showMessageDialog(this, "Add success!");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "IO error!");
        } finally {
            wordField.setText("");
            meaningField.setText("");
        }
    }

    public void Update() {
        String word = wordField.getText();
        word = word.trim().toLowerCase();
        if(!inputCheck(word)){
            return;
        }
        String meanings_s = meaningField.getText();
        if (meanings_s.isEmpty() || meanings_s.isBlank()) {
            JOptionPane.showMessageDialog(this, "Empty meanings, please input again");
            return;
        }

        String[] meanings = meanings_s.split(";");
        ArrayList<String> meanings_list = new ArrayList<>();
        for(String meaning: meanings) {
            meanings_list.add(meaning);
        }
        ErrorCode error_code = ErrorCode.SUCCESS;
        StringBuilder error_context = new StringBuilder("");
        try{
            if (!client.Update(word, meanings_list, error_code, error_context)) {
                JOptionPane.showMessageDialog(this, error_context);
                return;
            }
            JOptionPane.showMessageDialog(this, "Update success!");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "IO Error!");
        } finally {
            wordField.setText("");
            meaningField.setText("");
        }
    }

    public void Remove() {
        String word = wordField.getText();
        if (!inputCheck(word)){
            return;
        }
        ErrorCode error_code = ErrorCode.SUCCESS;
        StringBuilder error_context = new StringBuilder("");
        try{
            if (!client.Remove(word, error_code, error_context)) {
                JOptionPane.showMessageDialog(this, error_context);
                return;
            }
            JOptionPane.showMessageDialog(this, "Remove success!");
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "IO Error!");
        } finally {
            wordField.setText("");
            meaningField.setText("");
        }
    }

    public void Query() {
        String word = wordField.getText();
        word = word.trim().toLowerCase();
        if (!inputCheck(word)) {
            return;
        }

        ArrayList<String> meanings_list = new ArrayList<>();
        ErrorCode error_code = ErrorCode.SUCCESS;
        StringBuilder error_context = new StringBuilder("");
        try{
            if (!client.Query(word, meanings_list, error_code, error_context)){
                System.out.println(error_context);
                JOptionPane.showMessageDialog(this, error_context);
                return;
            }
            StringBuilder meaning = new StringBuilder();
            for (int i = 0;  i < meanings_list.size(); i++) {
                meaning.append(meanings_list.get(i)).append(";");
            }
            meaningField.setText(meaning.toString());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "IO Error!");
        }
    }
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java UserGUI <server name> <port number>");
            System.exit(1);
        }
        UserGUI gui = new UserGUI(args[0], Integer.parseInt(args[1]));
    }
}
