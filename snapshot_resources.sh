#!/bin/bash

# Create output file with timestamp
OUTPUT_FILE="termux_resources_$(date +%Y%m%d_%H%M%S).txt"

{
    echo "========================================="
    echo "TERMUX RESOURCE FILES SNAPSHOT"
    echo "Generated: $(date)"
    echo "========================================="
    echo ""

    # Function to print file contents with header
    print_file() {
        if [ -f "$1" ]; then
            echo ""
            echo "========================================="
            echo "FILE: $1"
            echo "========================================="
            echo ""
            cat "$1"
            echo ""
        else
            echo ""
            echo "========================================="
            echo "FILE NOT FOUND: $1"
            echo "========================================="
            echo ""
        fi
    }

    # Navigate to app/src/main/res
    cd app/src/main/res 2>/dev/null || {
        echo "ERROR: Cannot find app/src/main/res directory"
        echo "Please run this script from your Termux project root"
        exit 1
    }

    echo "CURRENT DIRECTORY: $(pwd)"
    echo ""

    # List all resource directories
    echo "========================================="
    echo "RESOURCE DIRECTORY STRUCTURE"
    echo "========================================="
    echo ""
    ls -la
    echo ""

    # Values directory files
    echo "========================================="
    echo "VALUES DIRECTORY FILES"
    echo "========================================="
    echo ""
    ls -la values/
    echo ""

    # Check for values-night
    if [ -d "values-night" ]; then
        echo "========================================="
        echo "VALUES-NIGHT DIRECTORY FILES"
        echo "========================================="
        echo ""
        ls -la values-night/
        echo ""
    fi

    # Drawable directory files
    echo "========================================="
    echo "DRAWABLE DIRECTORY FILES"
    echo "========================================="
    echo ""
    ls -la drawable/
    echo ""

    # Check for drawable-v24
    if [ -d "drawable-v24" ]; then
        echo "========================================="
        echo "DRAWABLE-V24 DIRECTORY FILES"
        echo "========================================="
        echo ""
        ls -la drawable-v24/
        echo ""
    fi

    # Layout directory files
    echo "========================================="
    echo "LAYOUT DIRECTORY FILES"
    echo "========================================="
    echo ""
    ls -la layout/
    echo ""

    # Font directory (if exists)
    if [ -d "font" ]; then
        echo "========================================="
        echo "FONT DIRECTORY FILES"
        echo "========================================="
        echo ""
        ls -la font/
        echo ""
    fi

    # Now print contents of important files
    echo ""
    echo "========================================="
    echo "========================================="
    echo "FILE CONTENTS"
    echo "========================================="
    echo "========================================="
    echo ""

    # Values files
    print_file "values/colors.xml"
    print_file "values/dimens.xml"
    print_file "values/styles.xml"
    print_file "values/themes.xml"
    print_file "values/attrs.xml"
    print_file "values/strings.xml"
    print_file "values/integers.xml"
    print_file "values/arrays.xml"
    print_file "values/typography.xml"
    print_file "values/fonts.xml"

    # Values-night files
    if [ -d "values-night" ]; then
        print_file "values-night/colors.xml"
        print_file "values-night/themes.xml"
        print_file "values-night/styles.xml"
    fi

    # Drawable files - list all XML drawables
    echo ""
    echo "========================================="
    echo "DRAWABLE XML FILES CONTENTS"
    echo "========================================="
    echo ""
    
    for file in drawable/*.xml; do
        if [ -f "$file" ]; then
            print_file "$file"
        fi
    done

    # Layout files
    echo ""
    echo "========================================="
    echo "LAYOUT XML FILES CONTENTS"
    echo "========================================="
    echo ""
    
    # Specifically these layout files
    for file in layout/drawer_ai_assistant.xml layout/item_ai_message.xml layout/activity_termux.xml; do
        print_file "$file"
    done

    # Also include any other relevant layout files
    for file in layout/*.xml; do
        # Skip if we already printed them
        if [[ "$file" != "layout/drawer_ai_assistant.xml" && 
              "$file" != "layout/item_ai_message.xml" && 
              "$file" != "layout/activity_termux.xml" ]]; then
            print_file "$file"
        fi
    done

    # Font files info (can't cat binary files)
    if [ -d "font" ]; then
        echo ""
        echo "========================================="
        echo "FONT FILES"
        echo "========================================="
        echo ""
        ls -la font/
        echo ""
        echo "Note: Font files are binary and not displayed"
    fi

    # Also get the terminal color scheme from device if possible
    echo ""
    echo "========================================="
    echo "TERMINAL COLOR SCHEME (if available)"
    echo "========================================="
    echo ""
    
    # Check common locations for termux color schemes
    if [ -f ~/.termux/colors.properties ]; then
        echo "~/.termux/colors.properties:"
        cat ~/.termux/colors.properties
    elif [ -f /data/data/com.termux/files/home/.termux/colors.properties ]; then
        echo "/data/data/com.termux/files/home/.termux/colors.properties:"
        cat /data/data/com.termux/files/home/.termux/colors.properties
    else
        echo "No custom color scheme found (using default)"
    fi

    echo ""
    echo "========================================="
    echo "END OF SNAPSHOT"
    echo "Generated: $(date)"
    echo "========================================="

} > "$OUTPUT_FILE"

echo "Resource snapshot saved to: $OUTPUT_FILE"
echo "File size: $(du -h "$OUTPUT_FILE" | cut -f1)"
