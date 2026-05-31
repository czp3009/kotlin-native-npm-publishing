#!/usr/bin/env node
'use strict';

const childProcess = require('child_process');
const fs = require('fs');
const path = require('path');

const kotlinTargetPrefixes = {
    linux: 'linux',
    darwin: 'macos',
    win32: 'mingw'
};

const targetPrefix = kotlinTargetPrefixes[process.platform];

if (!targetPrefix) {
    console.error(`Unsupported platform: ${process.platform}-${process.arch}`);
    process.exit(1);
}

const mainPackageJson = require(path.join(__dirname, '..', 'package.json'));
const platformSuffix = `${targetPrefix}-${process.arch}`;
const slashIndex = mainPackageJson.name.indexOf('/');
const packageName = mainPackageJson.name.startsWith('@')
    ? `${mainPackageJson.name.substring(0, slashIndex)}/${mainPackageJson.name.substring(slashIndex + 1)}-${platformSuffix}`
    : `${mainPackageJson.name}-${platformSuffix}`;

let packageRoot;
try {
    packageRoot = path.dirname(require.resolve(`${packageName}/package.json`));
} catch (error) {
    console.error(`Missing platform package: ${packageName}`);
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
