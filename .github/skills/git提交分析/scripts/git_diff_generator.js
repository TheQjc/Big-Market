const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// Get commit IDs from command line arguments
const commitIds = process.argv.slice(2);

if (commitIds.length === 0) {
    console.error('Please provide at least one commit ID.');
    process.exit(1);
}

try {
    // 1. Get current branch name
    let branchName = execSync('git rev-parse --abbrev-ref HEAD').toString().trim();
    // Sanitize branch name for file system
    branchName = branchName.replace(/[^a-zA-Z0-9\-_]/g, '_');

    // 2. Create target directory
    const projectRoot = path.resolve(__dirname, '../../../../'); // Assuming script is in .github/skills/git提交分析/scripts/
    const targetDir = path.join(projectRoot, 'docs', 'project_analysis', branchName);

    if (!fs.existsSync(targetDir)) {
        fs.mkdirSync(targetDir, { recursive: true });
        console.log(`Created directory: ${targetDir}`);
    } else {
        console.log(`Directory already exists: ${targetDir}`);
    }

    // 3. Generate diff files for each commit
    commitIds.forEach((commitId, index) => {
        const diffFileName = `diff_${index + 1}.txt`;
        const diffFilePath = path.join(targetDir, diffFileName);
        
        // Using "git show" to get the diff
        // Adding --no-color to avoid ANSI codes in the text file
        const command = `git show --no-color ${commitId}`;
        
        console.log(`Processing commit: ${commitId} -> ${diffFileName}`);
        
        try {
            const diffContent = execSync(command, { cwd: projectRoot }).toString();
            fs.writeFileSync(diffFilePath, diffContent);
        } catch (error) {
            console.error(`Error processing commit ${commitId}: ${error.message}`);
            // Generate a placeholder file with error or partial content if possible
            fs.writeFileSync(diffFilePath, `Error retrieving commit ${commitId}\n${error.message}`);
        }
    });

    console.log('Diff generation complete.');
    console.log(`Output directory: ${targetDir}`);

} catch (error) {
    console.error(`An error occurred: ${error.message}`);
    process.exit(1);
}
