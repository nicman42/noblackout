package com.heideblitz.noblackout;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Comparator;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoBlackout {
	private static final Logger log = LoggerFactory.getLogger(NoBlackout.class);

	private static final String PROPERTY_ROOT = "root";
	private static final String PROPERTY_ROOT_DEFAULT = "C:\\Program Files (x86)\\Steam\\steamapps\\common";

	private static final int CHECK_INTERVAL_SECONDS = 30;

	private static final Color COLOR_RUNNING = new Color(0, 200, 0);
	private final Image icon = ImageIO.read(NoBlackout.class.getResourceAsStream("/icon_16.png"));
	private final Image icon_active = ImageIO.read(NoBlackout.class.getResourceAsStream("/active_16.png"));

	private final Properties properties = new Properties();
	private final File confFile = new File(System.getProperty("user.home"), ".noblackout");

	private Robot robot = new Robot();
	private JFrame wnd;
	private JTextField rootTextField = new JTextField();
	private JButton chooseBtn;
	private TrayIcon trayIcon;
	private Set<String> exePaths = new TreeSet<String>(new Comparator<String>() {
		public int compare(String o1, String o2) {
			return o1.compareToIgnoreCase(o2);
		}
	});
	private DefaultListModel<String> exePathsListModel = new DefaultListModel<String>();
	private JTextField statusTextField = new JTextField();

	public static void main(String[] args) throws AWTException, InterruptedException, IOException {
		new NoBlackout();
	}

	public NoBlackout() throws AWTException, InterruptedException, IOException {
		PopupMenu popup = new PopupMenu();
		MenuItem mi;
		
		popup.add(mi = new MenuItem("Configuration"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				config();
			}
		});
		popup.add(mi = new MenuItem("Exit"));
		mi.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
		});
		trayIcon = new TrayIcon(icon, null, popup);
		trayIcon.setImageAutoSize(true);
		SystemTray.getSystemTray().add(trayIcon);

		initConfigWindow();

		if (confFile.exists()) {
			properties.load(new FileInputStream(confFile));
		}
		File file = new File(properties.getProperty(PROPERTY_ROOT, PROPERTY_ROOT_DEFAULT));
		if (file.exists()) {
			setRootDirectory(file);
		} else {
			config();
		}

		while (true) {
			check();
			Thread.sleep(1000 * CHECK_INTERVAL_SECONDS);
		}
	}

	private void initConfigWindow() {
		wnd = new JFrame("NoBlackout");
		JPanel panel = new JPanel(new GridBagLayout());
		wnd.setContentPane(panel);

		rootTextField.setEditable(false);
		GridBagConstraints gbc1 = new GridBagConstraints();
		gbc1.gridx = 0;
		gbc1.weightx = 1;
		gbc1.fill = GridBagConstraints.HORIZONTAL;
		gbc1.insets = new Insets(3, 3, 3, 3);
		GridBagConstraints gbc2 = new GridBagConstraints();
		gbc2.gridx = 1;
		gbc2.fill = GridBagConstraints.HORIZONTAL;
		gbc2.insets = gbc1.insets;
		panel.add(rootTextField, gbc1);

		panel.add(chooseBtn = new JButton(new AbstractAction("Choose") {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent event) {
				chooseBtn.setEnabled(false);
				String root = properties.getProperty(PROPERTY_ROOT, PROPERTY_ROOT_DEFAULT);
				JFileChooser fileChooser = new JFileChooser();
				if (root != null && !root.trim().isEmpty()) {
					fileChooser.setCurrentDirectory(new File(root));
				}
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (fileChooser.showOpenDialog(wnd) == JFileChooser.APPROVE_OPTION) {
					try {
						File rootFile = fileChooser.getSelectedFile();
						properties.setProperty(PROPERTY_ROOT, rootFile.getCanonicalPath());
						properties.store(new FileOutputStream(confFile), "");
						setRootDirectory(rootFile);
					} catch (IOException e) {
						log.error(e.getMessage());
					}
				}
			}
		}), gbc2);

		panel.add(statusTextField, gbc1);
		statusTextField.setEditable(false);

		panel.add(new JButton(new AbstractAction("Check now") {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent event) {
				try {
					check();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
			}

		}), gbc2);

		GridBagConstraints gbc3 = new GridBagConstraints();
		gbc3.gridx = 0;
		gbc3.gridwidth = 2;
		gbc3.insets = gbc1.insets;
		gbc3.fill = GridBagConstraints.BOTH;
		gbc3.weightx = 1;
		gbc3.weighty = 1;

		JList<String> exePathsList = new JList<String>(exePathsListModel);
		exePathsList.setEnabled(false);
		panel.add(new JScrollPane(exePathsList), gbc3);


		wnd.setSize(400, 300);
		wnd.setLocationRelativeTo(null);
	}

	private void config() {
		wnd.setVisible(true);
	}

	private void check() throws IOException {
		String processPath = findRunningProcess();
		String msg;
		if (processPath == null) {
			msg = "found no running process";
			statusTextField.setForeground(Color.BLACK);
			trayIcon.setImage(icon);
		} else {
			msg = "found running process: " + processPath;
			statusTextField.setForeground(COLOR_RUNNING);
			trayIcon.setImage(icon_active);
			simulateActivity();
		}

		log.info(msg);
		trayIcon.setToolTip(msg);
		statusTextField.setText(msg);
	}

	private void setRootDirectory(final File rootDirectory) throws IOException {
		exePaths.clear();

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				exePathsListModel.clear();
				rootTextField.setText("");
			}
		});

		if (!rootDirectory.isDirectory()) {
			return;
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					rootTextField.setText(rootDirectory.getCanonicalPath());
				} catch (IOException e) {
					log.error(e.getMessage());
					rootTextField.setText("");
				}
			}
		});

		innitExePaths(rootDirectory);
		for (final String path : exePaths) {
			log.info(path);
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					exePathsListModel.addElement(path);
				}
			});
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				chooseBtn.setEnabled(true);
			}
		});
	}

	private void innitExePaths(File directory) {
		for (File file : directory.listFiles()) {
			if (file.isDirectory()) {
				innitExePaths(file);
			} else if (file.getName().endsWith(".exe")) {
				try {
					exePaths.add(file.getCanonicalPath());
				} catch (IOException e) {
					log.error(e.getMessage());
				}
			}
		}
	}

	private String findRunningProcess() throws IOException {
		Process taskmanagerProcess = Runtime.getRuntime().exec("wmic process get executablepath");
		BufferedReader input = new BufferedReader(new InputStreamReader(taskmanagerProcess.getInputStream()));
		for (String path; (path = input.readLine()) != null;) {
			path = path.trim();
			if (path.isEmpty()) {
				continue;
			}
			if (exePaths.contains(path)) {
				return path;
			}
		}
		input.close();

		return null;
	}

	private void simulateActivity() {
		log.info("simulate mouse movement");
		Point p = MouseInfo.getPointerInfo().getLocation();
		robot.mouseMove(p.x, p.y + 1);
		robot.mouseMove(p.x, p.y);
	}
}
