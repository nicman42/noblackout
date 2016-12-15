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
import java.io.FileNotFoundException;
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
import javax.swing.SwingWorker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoBlackout {
	private static final Logger log = LoggerFactory.getLogger(NoBlackout.class);

	private static final String PROPERTY_ROOT = "root";
	private static final String PROPERTY_ROOT_DEFAULT = "C:\\Program Files (x86)\\Steam\\steamapps\\common";

	private static final int CHECK_INTERVAL_SECONDS = 30;

	private static final Color COLOR_RUNNING = new Color(0, 200, 0);
	private final Image icon = ImageIO.read(NoBlackout.class.getResourceAsStream("/icon_16.png"));

	private final Properties properties = new Properties();
	private final File confFile = new File(System.getProperty("user.home"), ".noblackout");

	private Robot robot = new Robot();
	private Point lastPoint = MouseInfo.getPointerInfo().getLocation();
	private JFrame wnd;
	
	private DefaultListModel<String> directoriesListModel = new DefaultListModel<String>();
	
	private JButton addBtn;
	private JButton removeBtn;
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
		for(String directory : properties.getProperty(PROPERTY_ROOT, PROPERTY_ROOT_DEFAULT).split(File.pathSeparator)){
			addDirectory(new File(directory));
		}

		while (true) {
			check(false);
			Thread.sleep(1000 * CHECK_INTERVAL_SECONDS);
		}
	}

	private void initConfigWindow() {
		wnd = new JFrame("NoBlackout");
		JPanel panel = new JPanel(new GridBagLayout());
		wnd.setContentPane(panel);

		GridBagConstraints gbc1 = new GridBagConstraints();
		gbc1.gridx = 0;
		gbc1.gridheight = 2;
		gbc1.weightx = 1;
		gbc1.fill = GridBagConstraints.BOTH;
		gbc1.insets = new Insets(3, 3, 3, 3);
		GridBagConstraints gbc2 = new GridBagConstraints();
		gbc2.gridx = 0;
		gbc2.fill = GridBagConstraints.HORIZONTAL;
		gbc2.insets = gbc1.insets;
		GridBagConstraints gbc3 = new GridBagConstraints();
		gbc3.gridx = 1;
		gbc3.fill = GridBagConstraints.HORIZONTAL;
		gbc3.anchor = GridBagConstraints.NORTH;
		gbc3.insets = gbc1.insets;
		
		final JList<String> directoriesList = new JList<String>(directoriesListModel);
		panel.add(new JScrollPane(directoriesList), gbc1);

		panel.add(addBtn = new JButton(new AbstractAction("Add") {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent event) {
				String root = properties.getProperty(PROPERTY_ROOT, PROPERTY_ROOT_DEFAULT);
				JFileChooser fileChooser = new JFileChooser();
				if (root != null && !root.trim().isEmpty()) {
					fileChooser.setCurrentDirectory(new File(root));
				}
				fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				if (fileChooser.showOpenDialog(wnd) == JFileChooser.APPROVE_OPTION) {
					try {
						File rootFile = fileChooser.getSelectedFile();
						addDirectory(rootFile);
						saveConfig();
					} catch (IOException e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}), gbc3);
		
		panel.add(removeBtn = new JButton(new AbstractAction("Remove") {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent event) {
				for(String directory : directoriesList.getSelectedValuesList()){
					directoriesListModel.removeElement(directory);
				}
				try{
					saveConfig();
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
				initExePaths();
			}
		}), gbc3);

		GridBagConstraints gbc4 = new GridBagConstraints();
		gbc4.gridx = 0;
		gbc4.gridwidth = 2;
		gbc4.insets = gbc1.insets;
		gbc4.fill = GridBagConstraints.BOTH;
		gbc4.weightx = 1;
		gbc4.weighty = 1;

		JList<String> exePathsList = new JList<String>(exePathsListModel);
		exePathsList.setEnabled(false);
		panel.add(new JScrollPane(exePathsList), gbc4);
		
		panel.add(statusTextField, gbc2);
		statusTextField.setEditable(false);

		panel.add(new JButton(new AbstractAction("Force check") {
			private static final long serialVersionUID = 1L;

			public void actionPerformed(ActionEvent event) {
				try {
					check(true);
				} catch (IOException e) {
					log.error(e.getMessage(), e);
				}
			}

		}), gbc3);

		wnd.setSize(400, 300);
		wnd.setLocationRelativeTo(null);
	}

	private void config() {
		wnd.setVisible(true);
	}
	
	private void saveConfig() throws FileNotFoundException, IOException{
		StringBuilder directories = new StringBuilder();
		for(int i=0; i<directoriesListModel.size(); i++){
			if(directories.length() > 0){
				directories.append(File.pathSeparator);
			}
			directories.append(directoriesListModel.getElementAt(i));
		}
		
		
		properties.setProperty(PROPERTY_ROOT, directories.toString());
		properties.store(new FileOutputStream(confFile), "");
	}

	private void check(boolean force) throws IOException {
		String msg;
		Point p = MouseInfo.getPointerInfo().getLocation();
		if (!force && !lastPoint.equals(p)) {
			msg = "mouse has been moved => nothing to do";
			statusTextField.setForeground(Color.BLACK);
			lastPoint = p;
		}else{
			String processPath = findRunningProcess();
			if (processPath == null) {
				msg = "found no running process";
				statusTextField.setForeground(Color.BLACK);
			} else {
				msg = "found running process: " + processPath;
				statusTextField.setForeground(COLOR_RUNNING);
				simulateActivity();
			}
		}

		log.info(msg);
		trayIcon.setToolTip(msg);
		statusTextField.setText(msg);
	}

	private void addDirectory(final File rootDirectory) throws IOException {
		if (!rootDirectory.isDirectory()) {
			return;
		}

		try {
			directoriesListModel.addElement(rootDirectory.getCanonicalPath());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		
		addExePaths(rootDirectory);
	}
	
	private void initExePaths() {
		exePaths.clear();
		exePathsListModel.clear();
		
		for(int i = 0; i < directoriesListModel.size(); i++){
			addExePaths(new File(directoriesListModel.getElementAt(i)));
		}
	}

	private void addExePaths(File rootDirectory) {
		if (rootDirectory.listFiles() != null) {
			for (File file : rootDirectory.listFiles()) {
				if (file.isDirectory()) {
					addExePaths(file);
				} else if (file.getName().endsWith(".exe")) {
					try {
						final String path = file.getCanonicalPath();
						log.info(path);
						
						exePaths.add(path);
					} catch (IOException e) {
						log.error(e.getMessage(), e);
					}
				}
			}
		}

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				exePathsListModel.clear();
				for(String path : exePaths){
					exePathsListModel.addElement(path);
				}
			}
		});
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
