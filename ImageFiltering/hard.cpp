#include <omp.h>
#include <fstream>
#include <iostream>
#include <algorithm>
#include <exception>
#include <cstdlib>
#include <string>
#include <ios>

#define SCHEDULE static

int NUM_OF_COLORS = 256;
char FIRST_CLUSTER_VALUE = 0;
char SECOND_CLUSTER_VALUE = 84;
char THIRD_CLUSTER_VALUE = -86;
char FOURTH_CLUSTER_VALUE = -1;

template<typename T>
struct triple {
    T first, second, third;
};

int main(int num_of_args, char *args[]) {
    if (num_of_args != 4) {
        std::cout << "4 arguments expected, found " << num_of_args;
        return 0;
    }
    int num_of_threads;
    try {
        num_of_threads = std::atoi(args[1]);
        if (num_of_threads > 0) {
            omp_set_num_threads(num_of_threads);
            omp_set_dynamic(num_of_threads);
        }
        if (num_of_threads < -1 || num_of_threads > 2000) {
            std::cout << "Illegal number of threads: " << num_of_threads;
            return 0;
        }
    } catch (...) {
        std::cout << "Illegal number of threads: " << args[1];
        return 0;
    }
    std::string in_file = args[2];
    std::string out_file = args[3];
    std::ifstream in;
    char *image, *file;
    int image_size, width, height;
    try {
        in.open(in_file);

        // getting length of file
        in.seekg(0, std::ios::end);
        long long length = in.tellg();
        in.seekg(0, std::ios::beg);

        // reading file
        file = new char[length];
        in.read(file, length);

        // reading supporting info
        in.seekg(0, std::ios::beg);
        std::string p5;
        in >> p5;
        int n;
        in >> width >> height >> n;
        image_size = width * height;
        image = file + length  - image_size;
        in.close();

        if (p5 != "P5") {
            std::cout << "Not PNM (P5) file";
            return 0;
        }
        if (n != 255) {
            std::cout << "Illegal file format";
            return 0;
        }

    } catch (std::exception const &e) {
        std::cout << "Cannot open/read input file: " << e.what() << '\n';
        return 0;
    }

    double start = omp_get_wtime();

    // histogram calculation
    int bar_graph[NUM_OF_COLORS];
    std::fill(bar_graph, bar_graph + NUM_OF_COLORS, 0);
#pragma omp parallel if (num_of_threads != -1)
    {
        int bar_graph_th[NUM_OF_COLORS];
        std::fill(bar_graph_th, bar_graph_th + NUM_OF_COLORS, 0);
#pragma omp for nowait schedule(SCHEDULE)
        for (int i = 0; i < image_size; ++i) {
            bar_graph_th[(unsigned char) image[i]]++;
        }
#pragma omp critical
        {
            for (int i = 0; i < NUM_OF_COLORS; ++i) {
                bar_graph[i] += bar_graph_th[i];
            }
        }
    }

    // supporting info calculation
    double probability[NUM_OF_COLORS + 1];
    double math_expects[NUM_OF_COLORS + 1];
    probability[0] = 0;
    math_expects[0] = 0;
    for (int i = 0; i < NUM_OF_COLORS; ++i) {
        probability[i + 1] = probability[i] + bar_graph[i];
        math_expects[i + 1] = math_expects[i] + bar_graph[i] * i;
    }

    // searching for the best combination of threshold
    triple<int> best_combination;
    double max_dispersion = -1;
#pragma omp parallel if (num_of_threads != -1)
    {
        triple<int> best_combination_th;
        double max_dispersion_th = -1;
        for (int f1 = 0; f1 < NUM_OF_COLORS - 3; ++f1) {
            for (int f2 = f1 + 1; f2 < NUM_OF_COLORS - 2; ++f2) {
#pragma omp for nowait schedule(SCHEDULE)
                    for (int f3 = f2 + 1; f3 < NUM_OF_COLORS - 1; ++f3) {
                    double dispersion = math_expects[f1 + 1] * math_expects[f1 + 1] / probability[f1 + 1] +
                                        (math_expects[f2 + 1] - math_expects[f1 + 1]) *
                                        (math_expects[f2 + 1] - math_expects[f1 + 1]) / (probability[f2 + 1] - probability[f1 + 1]) +
                                        (math_expects[f3 + 1] - math_expects[f2 + 1]) *
                                        (math_expects[f3 + 1] - math_expects[f2 + 1]) / (probability[f3 + 1] - probability[f2 + 1]) +
                                        (math_expects[NUM_OF_COLORS] - math_expects[f3 + 1]) *
                                        (math_expects[NUM_OF_COLORS] - math_expects[f3 + 1]) / (probability[NUM_OF_COLORS] - probability[f3 + 1]);
                    if (dispersion > max_dispersion_th) {
                        max_dispersion_th = dispersion;
                        best_combination_th = {f1, f2, f3};
                    }
                }
            }
        }
#pragma omp critical
        {
            if (max_dispersion_th > max_dispersion) {
                max_dispersion = max_dispersion_th;
                best_combination = best_combination_th;
            }
        }
    }

    // building new image
#pragma omp parallel for if (num_of_threads != -1) schedule(SCHEDULE)
    for (int i = 0; i < image_size; ++i) {
        int pixel = (unsigned char) image[i];
        if (pixel <= best_combination.first) {
            image[i] = FIRST_CLUSTER_VALUE;
        } else if (pixel <= best_combination.second) {
            image[i] = SECOND_CLUSTER_VALUE;
        } else if (pixel <= best_combination.third) {
            image[i] = THIRD_CLUSTER_VALUE;
        } else {
            image[i] = FOURTH_CLUSTER_VALUE;
        }
    }

    double end = omp_get_wtime();

    std::ofstream out;
    try {
        out.open(out_file);
        out << "P5\n" << width << ' ' << height << "\n255\n";
        out.write(image, image_size);
        out.close();
    } catch (std::exception const &e) {
        std::cout << "Cannot open/read output file: " << e.what() << '\n';
    }

    delete[] file;
    printf("%u %u %u\n", best_combination.first, best_combination.second, best_combination.third);
    printf("Time (%i thread(s)): %g ms\\n", (num_of_threads == -1) ? 0 : omp_get_max_threads(), (end - start) * 1000);

    return 0;
}
