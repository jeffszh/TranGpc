# coding=utf-8
import os
import sys
import time
import matplotlib.image as mp_img

inputFilePath = "TTN2/YOHGAS/GPC/"
outputFilePath = "png/"


def main():
    print("开始转换……")
    t1 = time.time()

    os.chdir("..")
    file_list = os.listdir(inputFilePath)
    for f in file_list:
        if f.endswith(".GPC"):
            gpc_file = f
            png_file = gpc_file.replace(".GPC", ".png")
            convert_gpc_to_png(inputFilePath + gpc_file, outputFilePath + png_file)

    t2 = time.time()
    print("转换完成。")
    print("用时：%d秒。" % (t2 - t1))


def convert_gpc_to_png(gpc_file, png_file):
    print "%s ===> %s" % (gpc_file, png_file)
    mp_img.imsave()


if __name__ == '__main__':
    sys.exit(main())
