#!/usr/bin/env node
'use strict';

const childProcess = require('child_process');
const fs = require('fs');
const path = require('path');

const mainPackageJson = require(path.join(__dirname, '..', 'package.json'));
const platformPackages = mainPackageJson.kotlinNativeNpmPublishing.platformPackages || {};
const platform = `${process.platform}-${process.arch}`;
const packageName = platformPackages[platform];

if (!packageName) {
    console.error(`Unsupported platform: ${platform}`);
    if (Object.keys(platformPackages).length === 0) {
        console.error('This package does not declare any native platform packages.');
    } else {
        console.error(`Supported platforms: ${Object.keys(platformPackages).sort().join(', ')}`);
    }
    process.exit(1);
}

let packageRoot;
try {
    packageRoot = path.dirname(require.resolve(`${packageName}/package.json`));
} catch (error) {
    console.error(`Missing platform package for ${platform}: ${packageName}`);
    console.error('Reinstall this npm package on the target platform.');
    process.exit(1);
}

const platformPackageJson = require(path.join(packageRoot, 'package.json'));
const binary = path.join(packageRoot, platformPackageJson.kotlinNativeNpmPublishing.binary);

if (process.platform !== 'win32') {
    try {
        fs.chmodSync(binary, 0o755);
    } catch (_) {
    }
}

const result = childProcess.spawnSync(binary, process.argv.slice(2), {stdio: 'inherit'});

if (result.error) {
    console.error(result.error.message);
    process.exit(1);
}

if (result.signal) {
    console.error(`Process terminated by signal ${result.signal}`);
    process.exit(1);
}

process.exit(result.status === null ? 1 : result.status);
