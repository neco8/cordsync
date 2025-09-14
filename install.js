#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const https = require('https');
const { execSync } = require('child_process');

// Configuration
const PACKAGE_JSON = require('./package.json');
const VERSION = PACKAGE_JSON.version;
const REPO_OWNER = 'neco8';
const REPO_NAME = 'cordsync';

// Detect platform
function getPlatform() {
  const platform = process.platform;
  const arch = process.arch;
  
  // Map Node.js values to our binary names
  const platformMap = {
    'darwin': 'darwin',
    'linux': 'linux',
    'win32': 'windows'
  };
  
  const archMap = {
    'x64': 'x64',
    'arm64': 'arm64'
  };
  
  const mappedPlatform = platformMap[platform];
  const mappedArch = archMap[arch];
  
  if (!mappedPlatform || !mappedArch) {
    throw new Error(`Unsupported platform: ${platform}-${arch}`);
  }
  
  // Windows arm64 is not supported
  if (platform === 'win32' && arch === 'arm64') {
    throw new Error('Windows ARM64 is not supported');
  }
  
  return `${mappedPlatform}-${mappedArch}`;
}

// Download file from URL
function download(url, dest) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(dest);
    
    https.get(url, (response) => {
      if (response.statusCode === 302 || response.statusCode === 301) {
        // Handle redirect
        download(response.headers.location, dest).then(resolve).catch(reject);
        return;
      }
      
      if (response.statusCode !== 200) {
        reject(new Error(`Failed to download: ${response.statusCode}`));
        return;
      }
      
      response.pipe(file);
      
      file.on('finish', () => {
        file.close(resolve);
      });
    }).on('error', (err) => {
      fs.unlink(dest, () => {}); // Delete the file on error
      reject(err);
    });
  });
}

async function install() {
  try {
    console.log('Installing cordsync...');
    
    // Detect platform
    const platform = getPlatform();
    console.log(`Detected platform: ${platform}`);
    
    // Construct download URL
    const binaryName = `cordsync-${platform}${process.platform === 'win32' ? '.exe' : ''}`;
    const downloadUrl = `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/download/v${VERSION}/${binaryName}`;
    
    console.log(`Downloading from: ${downloadUrl}`);
    
    // Create dist directory
    const distDir = path.join(__dirname, 'dist');
    if (!fs.existsSync(distDir)) {
      fs.mkdirSync(distDir);
    }
    
    // Download binary
    const binaryPath = path.join(distDir, process.platform === 'win32' ? 'cordsync.exe' : 'cordsync');
    await download(downloadUrl, binaryPath);
    
    // Make executable on Unix
    if (process.platform !== 'win32') {
      fs.chmodSync(binaryPath, '755');
    }
    
    console.log('Installation complete!');
    
  } catch (error) {
    console.error('Installation failed:', error.message);
    console.error('Please install manually from:', `https://github.com/${REPO_OWNER}/${REPO_NAME}/releases`);
    // Don't exit with error to allow npm install to continue
  }
}

// Run installation
install();